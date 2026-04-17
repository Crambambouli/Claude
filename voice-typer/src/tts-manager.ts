import { spawn, ChildProcess } from 'child_process';
import * as fs   from 'fs';
import * as os   from 'os';
import * as path from 'path';
import { logger } from './logger';

export class TtsManager {
  private proc:    ChildProcess | null = null;
  private vbsPath: string;
  private txtPath: string;

  constructor() {
    this.vbsPath = path.join(os.tmpdir(), 'blitztext-tts.vbs');
    this.txtPath = path.join(os.tmpdir(), 'blitztext-tts-text.txt');

    // VBScript einmalig schreiben – liest Text aus Datei, kein String-Escaping nötig
    const vbs = [
      'Dim fso, f, sapi',
      'Set fso = CreateObject("Scripting.FileSystemObject")',
      `Set f   = fso.OpenTextFile("${this.txtPath}", 1, False, -1)`,
      'Set sapi = CreateObject("SAPI.SpVoice")',
      'sapi.Speak f.ReadAll',
      'f.Close',
    ].join('\r\n');
    fs.writeFileSync(this.vbsPath, vbs, 'utf8');
  }

  speak(text: string, onDone?: () => void): void {
    this.stop();
    if (!text.trim()) return;

    // Text als UTF-16 LE mit BOM schreiben – VBScript OpenTextFile(..., -1) liest Unicode
    fs.writeFileSync(this.txtPath, '\ufeff' + text, 'utf16le');

    this.proc = spawn('wscript.exe', [this.vbsPath], { windowsHide: true });
    this.proc.on('exit', () => { this.proc = null; onDone?.(); });

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
    for (const p of [this.vbsPath, this.txtPath]) {
      try { fs.unlinkSync(p); } catch { /* ignore */ }
    }
  }
}
