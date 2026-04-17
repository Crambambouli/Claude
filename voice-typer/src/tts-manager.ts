import { spawn, ChildProcess } from 'child_process';
import * as fs   from 'fs';
import * as os   from 'os';
import * as path from 'path';
import { logger } from './logger';

export class TtsManager {
  private proc:         ChildProcess | null = null;
  private vbsPath:      string;
  private txtPath:      string;
  private replacements: Record<string, string> = {};

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

  setReplacements(r: Record<string, string>): void {
    this.replacements = r;
  }

  private applyReplacements(text: string): string {
    let result = text;
    for (const [from, to] of Object.entries(this.replacements)) {
      if (!from.trim()) continue;
      result = result.replace(new RegExp(`\\b${from}\\b`, 'gi'), to);
    }
    return result;
  }

  speak(text: string, onDone?: () => void): void {
    this.stop();
    if (!text.trim()) return;

    const cleaned = this.applyReplacements(TtsManager.cleanForSpeech(text));
    if (!cleaned) return;

    // Text als UTF-16 LE mit BOM schreiben – VBScript OpenTextFile(..., -1) liest Unicode
    fs.writeFileSync(this.txtPath, '\ufeff' + cleaned, 'utf16le');

    this.proc = spawn('wscript.exe', [this.vbsPath], { windowsHide: true });
    this.proc.on('exit', () => { this.proc = null; onDone?.(); });

    logger.info(`TTS gestartet: "${cleaned.slice(0, 60)}"`);
  }

  static cleanForSpeech(text: string): string {
    return text
      // Zeilenumbrüche normalisieren (Windows \r\n → \n)
      .replace(/\r\n/g, '\n')
      // Markdown-Überschriften
      .replace(/^#{1,6}\s+/gm, '')
      // Fett/Kursiv (**text** / *text*)
      .replace(/\*{1,2}(.+?)\*{1,2}/g, '$1')
      // Bullet-Zeilen: Symbol entfernen, Punkt dahinter → SAPI pausiert nach Punkten
      .replace(/^[\s]*[-•·▸▹►◆*+]\s+(.+)/gm, '$1. ')
      // Übrige einzelne Bullet-Zeichen entfernen
      .replace(/[•·▸▹►◆*]/g, '')
      // Schrägstrich zwischen Wörtern → Leerzeichen ("A / B" → "A B")
      .replace(/\s*\/\s*/g, ' ')
      // Doppelpunkt am Zeilenende → Punkt
      .replace(/:(\s*\n)/g, '.$1')
      // Mehrfache Leerzeilen → Pause
      .replace(/\n{2,}/g, '. ')
      // Einzelne Zeilenumbrüche → Leerzeichen
      .replace(/\n/g, ' ')
      // Doppelte Punkte bereinigen
      .replace(/\.\s*\./g, '.')
      // Mehrfache Leerzeichen bereinigen
      .replace(/\s{2,}/g, ' ')
      .trim();
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
