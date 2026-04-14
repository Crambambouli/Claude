import { spawnSync, spawn, ChildProcess } from 'child_process';
import * as http from 'http';
import * as fs   from 'fs';
import * as path from 'path';
import * as os   from 'os';
import { logger } from './logger';

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

// ─── HTTP-Helpers (nur Node-Builtins) ────────────────────────────────────────

function httpGetText(url: string, timeoutMs: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const req = http.get(url, (res) => {
      let d = '';
      res.on('data', c => d += c);
      res.on('end', () => resolve(d));
    });
    req.setTimeout(timeoutMs, () => { req.destroy(); reject(new Error('timeout')); });
    req.on('error', reject);
  });
}

function httpPostBuffer(
  port: number, path: string,
  body: Buffer, headers: Record<string, string>,
  timeoutMs: number,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { hostname: '127.0.0.1', port, path, method: 'POST',
        headers: { 'Content-Length': body.length, ...headers } },
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
const SERVER_STARTUP = 45_000;  // ms – Zeit für Modell-Download beim ersten Start

export class WhisperService {
  private whisperPath = '';
  private model       = 'base';
  private language    = 'auto';

  // Server-Zustand
  private serverProc:   ChildProcess | null = null;
  private serverReady   = false;
  private serverStarting: Promise<void> | null = null;

  constructor(whisperPath: string, model = 'base', language = 'auto') {
    this.whisperPath = whisperPath;
    this.model       = model;
    this.language    = language;
  }

  setPath(p: string):     void {
    if (p !== this.whisperPath) {
      this.whisperPath = p;
      this.stopServer();   // Neustart mit neuer Konfiguration erzwingen
    }
  }
  setModel(m: string):    void { this.model = m; this.stopServer(); }
  setLanguage(l: string): void { this.language = l; }

  // ─── Öffentliche API ───────────────────────────────────────────────────────

  /** Startet den Server im Hintergrund (Modell ins RAM laden). */
  async warmUp(): Promise<void> {
    if (!this.useServerMode()) return;
    await this.ensureServer();
  }

  /** Transkribiert einen WAV-Buffer. Nutzt Server wenn möglich, sonst CLI. */
  async transcribe(audioBuffer: Buffer): Promise<string> {
    if (this.useServerMode()) {
      try {
        return await this.transcribeViaServer(audioBuffer);
      } catch (err) {
        logger.warn('Server-Transkription fehlgeschlagen, Fallback auf CLI.', err);
        this.serverReady = false;
      }
    }
    return this.transcribeViaCli(audioBuffer);
  }

  checkInstallation(): { ok: boolean; message: string } {
    const cmd = this.resolveCliCmd();
    const res = spawnSync(cmd, ['--help'], { timeout: 5_000, encoding: 'utf8' });
    if (res.error || res.status === null) {
      return { ok: false, message: `Whisper nicht gefunden: "${cmd}". pip install faster-whisper` };
    }
    return { ok: true, message: `Whisper gefunden: ${cmd}` };
  }

  /** Berechnet RMS-Energie eines WAV-Buffers (44-Byte-Header überspringen). */
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

  // ─── Server-Modus ──────────────────────────────────────────────────────────

  /** Server-Modus aktiv wenn whisper_server.py konfiguriert oder kein Pfad. */
  private useServerMode(): boolean {
    // Immer Server-Modus (außer wenn explizit ein anderes Binary angegeben)
    const p = this.whisperPath;
    if (!p) return true;                    // Standard: server
    if (p.endsWith('whisper_server.py')) return true;
    if (p.endsWith('whisper_fast.py'))   return false;  // CLI-Wrapper
    return false;
  }

  private serverScriptPath(): string {
    const p = this.whisperPath;
    if (p && p.endsWith('whisper_server.py')) return p;
    // Standard-Pfad relativ zur App
    const candidates = [
      path.join(process.resourcesPath ?? '', 'whisper_server.py'),
      path.join(__dirname, '..', 'whisper_server.py'),
      path.join(__dirname, '..', '..', 'whisper_server.py'),
    ];
    return candidates.find(c => fs.existsSync(c))
      ?? path.join(__dirname, '..', '..', 'whisper_server.py');
  }

  private async ensureServer(): Promise<void> {
    if (this.serverReady && await this.isServerAlive()) return;

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

  private async startServer(): Promise<void> {
    this.stopServer();

    const script = this.serverScriptPath();
    if (!fs.existsSync(script)) {
      throw new Error(`whisper_server.py nicht gefunden: ${script}`);
    }

    const python = process.platform === 'win32' ? 'python' : 'python3';
    const lang   = this.language === 'auto' ? '' : this.language;
    const args   = [script, this.model, String(SERVER_PORT), lang];

    logger.info(`Starte Whisper-Server: ${python} ${args.join(' ')}`);

    this.serverProc = spawn(python, args, {
      stdio:    ['ignore', 'pipe', 'pipe'],
      detached: false,
    });

    this.serverProc.stdout?.on('data', (d: Buffer) => logger.info(`[whisper-server] ${d.toString().trim()}`));
    this.serverProc.stderr?.on('data', (d: Buffer) => logger.warn(`[whisper-server] ${d.toString().trim()}`));
    this.serverProc.on('exit', (code) => {
      logger.warn(`Whisper-Server beendet (Code ${code}).`);
      this.serverReady = false;
      this.serverProc  = null;
    });

    // Warten bis Server antwortet
    const deadline = Date.now() + SERVER_STARTUP;
    while (Date.now() < deadline) {
      await sleep(500);
      if (await this.isServerAlive()) {
        this.serverReady = true;
        logger.info('Whisper-Server bereit.');
        return;
      }
    }
    throw new Error('Whisper-Server hat nicht rechtzeitig geantwortet.');
  }

  private async isServerAlive(): Promise<boolean> {
    try {
      const r = await httpGetText(`http://127.0.0.1:${SERVER_PORT}/health`, 1_000);
      return r.trim() === 'ok';
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

    const lang = this.language === 'auto' ? 'auto' : this.language;
    const body = await httpPostBuffer(
      SERVER_PORT, '/transcribe', audioBuffer,
      { 'Content-Type': 'application/octet-stream', 'X-Language': lang },
      60_000,
    );

    const json = JSON.parse(body) as { text?: string; error?: string };
    if (json.error) throw new Error(json.error);
    const text = json.text?.trim() ?? '';
    logger.info(`Server-Transkript: "${text}"`);
    return text;
  }

  // ─── CLI-Modus (Fallback) ─────────────────────────────────────────────────

  private async transcribeViaCli(audioBuffer: Buffer): Promise<string> {
    const tmpDir  = os.tmpdir();
    const stamp   = Date.now();
    const wavFile = path.join(tmpDir, `vt-audio-${stamp}.wav`);
    const txtFile = path.join(tmpDir, `vt-audio-${stamp}.txt`);

    try {
      fs.writeFileSync(wavFile, audioBuffer);
      const cmd  = this.resolveCliCmd();
      const args = this.buildCliArgs(wavFile, tmpDir);
      logger.info(`CLI: ${cmd} ${args.join(' ')}`);

      const result = spawnSync(cmd, args, { timeout: 120_000, encoding: 'utf8', env: { ...process.env } });
      if (result.error) throw result.error;
      if ((result.status ?? 1) !== 0) {
        throw new Error(`Whisper Fehlercode ${result.status}: ${result.stderr?.trim()}`);
      }

      if (fs.existsSync(txtFile)) return fs.readFileSync(txtFile, 'utf8').trim();
      return result.stdout?.trim() ?? '';
    } finally {
      for (const f of [wavFile, txtFile]) {
        try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch { /* ignore */ }
      }
    }
  }

  private resolveCliCmd(): string {
    const p = this.whisperPath;
    if (!p) return process.platform === 'win32' ? 'whisper.exe' : 'whisper';
    if (p.endsWith('.py')) return process.platform === 'win32' ? 'python' : 'python3';
    if (fs.existsSync(p) && fs.statSync(p).isDirectory()) {
      const w = path.join(p, 'whisper.exe');
      return fs.existsSync(w) ? w : path.join(p, 'whisper');
    }
    return p;
  }

  private buildCliArgs(audioFile: string, outputDir: string): string[] {
    const extra = this.whisperPath?.endsWith('.py') ? [this.whisperPath] : [];
    const args = [
      ...extra, audioFile,
      '--model', this.model,
      '--output_format', 'txt',
      '--output_dir', outputDir,
      '--task', 'transcribe',
      '--condition_on_previous_text', 'False',
      '--no_speech_threshold', '0.6',
      '--logprob_threshold', '-1.0',
      '--compression_ratio_threshold', '2.4',
    ];
    if (this.language && this.language !== 'auto') args.push('--language', this.language);
    return args;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise(r => setTimeout(r, ms));
}
