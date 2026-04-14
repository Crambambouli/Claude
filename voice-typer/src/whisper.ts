import { spawnSync } from 'child_process';

/**
 * Bekannte Whisper-Halluzinationen bei Stille oder sehr leisem Audio.
 * Whisper erfindet wiederkehrende Phrasen aus seinem Trainingsdaten-Kontext.
 */
export const HALLUCINATION_PATTERN = /^(untertitel|subtitles?|captions?|transcribed|amara\.org|vielen dank|thank you|♪|\.{3,}|\[.{0,30}\]|im auftrag|auf wiedersehen|tschüss|bye|end of|www\.|http)/i;
import * as fs   from 'fs';
import * as path from 'path';
import * as os   from 'os';
import { logger } from './logger';

export class WhisperService {
  private whisperPath: string;
  private model:    string;
  private language: string;

  constructor(whisperPath: string, model = 'base', language = 'auto') {
    this.whisperPath = whisperPath;
    this.model       = model;
    this.language    = language;
  }

  setPath(p: string):     void { this.whisperPath = p; }
  setModel(m: string):    void { this.model = m; }
  setLanguage(l: string): void { this.language = l; }

  /**
   * Transkribiert einen WAV-Buffer.
   * Schreibt Audio in eine Temp-Datei, ruft das Whisper-CLI auf und
   * liest das Ergebnis aus der .txt-Ausgabedatei.
   */
  async transcribe(audioBuffer: Buffer): Promise<string> {
    const tmpDir   = os.tmpdir();
    const stamp    = Date.now();
    const wavFile  = path.join(tmpDir, `vt-audio-${stamp}.wav`);
    const txtFile  = path.join(tmpDir, `vt-audio-${stamp}.txt`);

    try {
      fs.writeFileSync(wavFile, audioBuffer);
      logger.info(`WAV gespeichert: ${wavFile} (${audioBuffer.length} Bytes)`);

      const whisperCmd = this.resolveWhisperCmd();
      logger.info(`Whisper-Befehl: ${whisperCmd}`);

      const args = this.buildArgs(wavFile, tmpDir);
      logger.debug(`Whisper-Argumente: ${args.join(' ')}`);

      const result = spawnSync(whisperCmd, args, {
        timeout:   120_000,
        encoding:  'utf8',
        env:       { ...process.env },
      });

      if (result.error) throw result.error;
      if (result.status !== 0) {
        const stderr = result.stderr?.trim() ?? '';
        throw new Error(
          `Whisper Fehlercode ${result.status}: ${stderr || 'Kein Output'}`
        );
      }

      // Whisper schreibt nach <output_dir>/<audioname>.txt
      if (!fs.existsSync(txtFile)) {
        // Fallback: aus stdout lesen
        const stdout = result.stdout?.trim() ?? '';
        logger.info(`Transkript (stdout): "${stdout}"`);
        return stdout;
      }

      const transcript = fs.readFileSync(txtFile, 'utf8').trim();
      logger.info(`Transkript: "${transcript}"`);
      return transcript;

    } finally {
      for (const f of [wavFile, txtFile]) {
        try { if (fs.existsSync(f)) fs.unlinkSync(f); } catch { /* ignore */ }
      }
    }
  }

  /** Überprüft, ob Whisper gefunden werden kann. */
  checkInstallation(): { ok: boolean; message: string } {
    const cmd = this.resolveWhisperCmd();
    const res = spawnSync(cmd, ['--help'], { timeout: 5_000, encoding: 'utf8' });
    if (res.error || res.status === null) {
      return {
        ok:      false,
        message: `Whisper nicht gefunden: "${cmd}". Bitte installieren: pip install openai-whisper`,
      };
    }
    return { ok: true, message: `Whisper gefunden: ${cmd}` };
  }

  private resolveWhisperCmd(): string {
    if (this.whisperPath) {
      // Wenn ein Verzeichnis angegeben wurde, hänge Binary-Name an
      const stat = fs.existsSync(this.whisperPath)
        ? fs.statSync(this.whisperPath)
        : null;
      if (stat?.isDirectory()) {
        const win = path.join(this.whisperPath, 'whisper.exe');
        const nix = path.join(this.whisperPath, 'whisper');
        return fs.existsSync(win) ? win : nix;
      }
      return this.whisperPath;
    }
    // Fallback: aus PATH
    return process.platform === 'win32' ? 'whisper.exe' : 'whisper';
  }

  private buildArgs(audioFile: string, outputDir: string): string[] {
    const args = [
      audioFile,
      '--model',                      this.model,
      '--output_format',              'txt',
      '--output_dir',                 outputDir,
      '--task',                       'transcribe',
      // Verhindert Halluzinationen bei Stille / leisem Audio
      '--condition_on_previous_text', 'False',
      '--no_speech_threshold',        '0.6',
      '--logprob_threshold',          '-1.0',
      '--compression_ratio_threshold','2.4',
    ];
    if (this.language && this.language !== 'auto') {
      args.push('--language', this.language);
    }
    return args;
  }

  /**
   * Berechnet den RMS-Pegel eines WAV-Buffers (überspringt den 44-Byte-Header).
   * Gibt einen Wert zwischen 0.0 und 1.0 zurück.
   */
  static rmsEnergy(wavBuffer: Buffer): number {
    if (wavBuffer.length <= 44) return 0;
    const samples = (wavBuffer.length - 44) / 2;
    let sum = 0;
    for (let i = 44; i < wavBuffer.length - 1; i += 2) {
      const s = wavBuffer.readInt16LE(i) / 32768;
      sum += s * s;
    }
    return Math.sqrt(sum / samples);
  }
}
