import { spawn, ChildProcess } from 'child_process';
import * as fs   from 'fs';
import * as os   from 'os';
import * as path from 'path';
import { logger } from './logger';

export class TtsManager {
  private proc:    ChildProcess | null = null;
  private vbsPath: string;

  constructor() {
    this.vbsPath = path.join(os.tmpdir(), 'blitztext-tts.vbs');
  }

  speak(text: string, onDone?: () => void): void {
    this.stop();

    if (!text.trim()) return;

    // Anführungszeichen escapen für VBScript-String
    const escaped = text.replace(/"/g, '" & Chr(34) & "');
    const vbs = `CreateObject("SAPI.SpVoice").Speak "${escaped}"`;
    fs.writeFileSync(this.vbsPath, vbs, 'utf8');

    this.proc = spawn('wscript.exe', [this.vbsPath], { windowsHide: true });

    this.proc.on('exit', () => {
      this.proc = null;
      onDone?.();
    });

    logger.info(`TTS gestartet: "${text.slice(0, 60)}"`);
  }

  stop(): void {
    if (this.proc) {
      this.proc.kill();
      this.proc = null;
      logger.info('TTS gestoppt.');
    }
  }

  isSpeaking(): boolean {
    return this.proc !== null;
  }

  destroy(): void {
    this.stop();
    try { fs.unlinkSync(this.vbsPath); } catch { /* ignore */ }
  }
}
