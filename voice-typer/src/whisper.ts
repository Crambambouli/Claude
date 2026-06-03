import { spawn, ChildProcess } from 'child_process';
import * as http from 'http';
import * as fs   from 'fs';
import * as path from 'path';
import { logger } from './logger';
import { ModelManager } from './model-manager';

// ─── Halluzinations-Filter ───────────────────────────────────────────────────

const HALLUCINATION_STRIPS: RegExp[] = [
  /untertitel im auftrag des zdf\.?/gi,
  /untertitel im auftrag von zdf\.?/gi,
  /untertitel:\s*[^\n]*/gi,
  /subtitles? by[^\n]*/gi,
  /captions? by[^\n]*/gi,
  /transcribed by[^\n]*/gi,
  /amara\.org[^\n]*/gi,
  /vielen dank f[üu]r (ihre|eure) aufmerksamkeit\.?/gi,
  /thank you for watching\.?/gi,
  /♪[^♪\n]*♪?/g,
  /\[musik\]/gi, /\[music\]/gi,
  /\[applaus\]/gi, /\[applause\]/gi,
  /\[gelächter\]/gi, /\[laughter\]/gi,
];

export function stripHallucinations(text: string): string {
  let r = text;
  for (const p of HALLUCINATION_STRIPS) r = r.replace(p, '');
  return r.replace(/\n{2,}/g, '\n').trim();
}

// ─── HTTP-Helpers ────────────────────────────────────────────────────────────

function httpGetJson(url: string, timeoutMs: number): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const req = http.get(url, (res) => {
      let d = '';
      res.on('data', c => d += c);
      res.on('end', () => {
        try { resolve(JSON.parse(d)); }
        catch { resolve({ _raw: d, _status: res.statusCode }); }
      });
    });
    req.setTimeout(timeoutMs, () => { req.destroy(); reject(new Error('timeout')); });
    req.on('error', reject);
  });
}

function buildMultipart(
  boundary: string,
  wavBuffer: Buffer,
  language: string,
): Buffer {
  const textField = (name: string, value: string): string =>
    `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`;

  const header =
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="file"; filename="audio.wav"\r\n` +
    `Content-Type: audio/wav\r\n\r\n`;
  const footer =
    `\r\n${textField('response_format', 'json')}` +
    `${textField('language', language)}` +
    `--${boundary}--\r\n`;

  return Buffer.concat([
    Buffer.from(header, 'utf8'),
    wavBuffer,
    Buffer.from(footer, 'utf8'),
  ]);
}

function httpPostMultipart(
  port: number,
  urlPath: string,
  body: Buffer,
  boundary: string,
  timeoutMs: number,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path: urlPath,
        method: 'POST',
        headers: {
          'Content-Type':   `multipart/form-data; boundary=${boundary}`,
          'Content-Length': body.length,
        },
      },
      (res) => {
        let d = '';
        res.on('data', c => d += c);
        res.on('end', () => resolve(d));
      },
    );
    req.setTimeout(timeoutMs, () => { req.destroy(); reject(new Error('timeout')); });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

// ─── WhisperService ───────────────────────────────────────────────────────────

const SERVER_PORT    = 8765;
const SERVER_STARTUP = 120_000; // ms – Modell liegt auf Disk, kein Download beim Start

export class WhisperService {
  // Rückwärtskompatible Felder – werden bei whisper.cpp nicht genutzt
  private language = 'de';

  private serverProc:     ChildProcess | null = null;
  private serverReady     = false;
  private serverStarting: Promise<void> | null = null;

  constructor(
    _whisperPath: string,
    _model = 'large-v3-q5_0',
    language = 'de',
  ) {
    this.language = language || 'de';
  }

  setPath(_p: string): void {
    // whisper.cpp nutzt festen Binary-Pfad – kein Neustart nötig
  }

  setModel(_m: string): void {
    // Modell ist fest (large-v3-q5_0) – kein Neustart nötig
  }

  setLanguage(l: string): void {
    this.language = l || 'de';
  }

  async warmUp(): Promise<void> {
    await this.ensureServer();
  }

  async transcribe(audioBuffer: Buffer): Promise<string> {
    return this.transcribeViaServer(audioBuffer);
  }

  checkInstallation(): { ok: boolean; message: string } {
    const bin = this.binaryPath();
    if (!fs.existsSync(bin)) {
      return { ok: false, message: `whisper-server.exe nicht gefunden: ${bin}. Bitte "npm run setup" ausführen.` };
    }
    if (!ModelManager.isDownloaded()) {
      return { ok: false, message: `Whisper-Modell fehlt: ${ModelManager.modelPath()}` };
    }
    return { ok: true, message: `whisper.cpp bereit (${bin})` };
  }

  static rmsEnergy(wavBuffer: Buffer): number {
    if (wavBuffer.length <= 44) return 0;
    let sum = 0;
    for (let i = 44; i < wavBuffer.length - 1; i += 2) {
      const s = wavBuffer.readInt16LE(i) / 32768;
      sum += s * s;
    }
    return Math.sqrt(sum / ((wavBuffer.length - 44) / 2));
  }

  destroy(): void { this.stopServer(); }

  // ─── Interne Methoden ─────────────────────────────────────────────────────

  private binaryPath(): string {
    // Produktion: extraResources legt whisper-server.exe in process.resourcesPath
    const prod = path.join(process.resourcesPath ?? '', 'whisper-server.exe');
    if (fs.existsSync(prod)) return prod;
    // Entwicklung: npm run setup legt whisper-server.exe in bin/ (Projekt-Root)
    return path.join(__dirname, '..', 'bin', 'whisper-server.exe');
  }

  private async ensureServer(): Promise<void> {
    if (await this.isServerAlive()) {
      this.serverReady = true;
      return;
    }

    if (this.serverStarting) {
      await this.serverStarting;
      return;
    }

    this.serverStarting = this.startServer();
    try {
      await this.serverStarting;
    } finally {
      this.serverStarting = null;
    }
  }

  private startServer(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.stopServer();

      const bin   = this.binaryPath();
      const model = ModelManager.modelPath();

      if (!fs.existsSync(bin)) {
        reject(new Error(`whisper-server.exe nicht gefunden: ${bin}. Bitte "npm run setup" ausführen.`));
        return;
      }
      if (!fs.existsSync(model)) {
        reject(new Error(`Whisper-Modell nicht gefunden: ${model}. Modell fehlt oder wurde nicht heruntergeladen.`));
        return;
      }

      const lang = this.language === 'auto' ? 'de' : this.language;
      const args = [
        '-m', model,
        '--port', String(SERVER_PORT),
        '--host', '127.0.0.1',
        '-l', lang,
        '-t', '4',
        '--convert',
      ];

      logger.info(`Starte whisper.cpp-Server: ${bin} ${args.join(' ')}`);

      const proc = spawn(bin, args, {
        stdio:       ['ignore', 'pipe', 'pipe'],
        detached:    false,
        windowsHide: true,
        shell:       false,
      });

      // ENOENT sofort ablehnen – nicht auf Timeout warten
      proc.on('error', (err: NodeJS.ErrnoException) => {
        this.serverProc  = null;
        this.serverReady = false;
        reject(new Error(
          err.code === 'ENOENT'
            ? `whisper-server.exe nicht gefunden: ${bin}`
            : `Whisper-Server Fehler: ${err.message}`,
        ));
      });

      proc.stdout?.on('data', (d: Buffer) => logger.info(`[whisper-server] ${d.toString().trim()}`));
      proc.stderr?.on('data', (d: Buffer) => logger.warn(`[whisper-server] ${d.toString().trim()}`));

      proc.on('exit', (code) => {
        logger.warn(`Whisper-Server beendet (Code ${code}).`);
        this.serverReady = false;
        this.serverProc  = null;
      });

      this.serverProc = proc;

      // Warten bis /health antwortet
      const deadline = Date.now() + SERVER_STARTUP;
      const poll = async (): Promise<void> => {
        if (await this.isServerAlive()) {
          this.serverReady = true;
          logger.info('Whisper-Server (whisper.cpp) bereit.');
          resolve();
          return;
        }
        if (Date.now() >= deadline) {
          this.stopServer();
          reject(new Error('Whisper-Server hat nicht rechtzeitig geantwortet (120 s).'));
          return;
        }
        setTimeout(() => { poll().catch(reject); }, 500);
      };

      // Kurze Verzögerung damit der Prozess starten kann, bevor wir pollen
      setTimeout(() => { poll().catch(reject); }, 300);
    });
  }

  private async isServerAlive(): Promise<boolean> {
    try {
      const res = await httpGetJson(`http://127.0.0.1:${SERVER_PORT}/health`, 1_000) as Record<string, unknown>;
      // Akzeptiere {"status":"ok"} oder jede 200-Antwort
      return res['status'] === 'ok' || '_status' in res;
    } catch { return false; }
  }

  private stopServer(): void {
    this.serverReady = false;
    if (this.serverProc) {
      try { this.serverProc.kill(); } catch { /* ignore */ }
      this.serverProc = null;
    }
  }

  private async transcribeViaServer(audioBuffer: Buffer): Promise<string> {
    await this.ensureServer();

    const lang     = this.language === 'auto' ? 'de' : this.language;
    const boundary = `----BlitztextBoundary${Date.now()}`;
    const body     = buildMultipart(boundary, audioBuffer, lang);

    const raw = await httpPostMultipart(SERVER_PORT, '/inference', body, boundary, 60_000);

    const json = JSON.parse(raw) as { text?: string; error?: string };
    if (json.error) throw new Error(json.error);

    const text = (json.text ?? '').trim();
    logger.info(`Transkript: "${text}"`);
    return text;
  }
}
