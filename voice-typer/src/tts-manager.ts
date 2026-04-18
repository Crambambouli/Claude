import { spawn, spawnSync, ChildProcess } from 'child_process';
import * as fs   from 'fs';
import * as os   from 'os';
import * as path from 'path';
import { logger } from './logger';

export class TtsManager {
  private proc:         ChildProcess | null = null;
  private vbsPath:      string;
  private txtPath:      string;
  private voicePath:    string;
  private replacements: Record<string, string> = {};

  constructor() {
    this.vbsPath   = path.join(os.tmpdir(), 'blitztext-tts.vbs');
    this.txtPath   = path.join(os.tmpdir(), 'blitztext-tts-text.txt');
    this.voicePath = path.join(os.tmpdir(), 'blitztext-tts-voice.txt');

    this.writeVbs();
  }

  private writeVbs(): void {
    const vbs = [
      'Dim fso, f, sapi, voices, i, voiceName',
      'Set fso = CreateObject("Scripting.FileSystemObject")',
      `Set f   = fso.OpenTextFile("${this.txtPath}", 1, False, -1)`,
      'Set sapi = CreateObject("SAPI.SpVoice")',
      'voiceName = ""',
      `If fso.FileExists("${this.voicePath}") Then`,
      '  Dim vf',
      `  Set vf = fso.OpenTextFile("${this.voicePath}", 1, False, 0)`,
      '  voiceName = Trim(vf.ReadAll())',
      '  vf.Close',
      'End If',
      'If voiceName <> "" Then',
      '  Set voices = sapi.GetVoices()',
      '  For i = 0 To voices.Count - 1',
      '    If InStr(1, voices.Item(i).GetDescription(), voiceName, 1) > 0 Then',
      '      Set sapi.Voice = voices.Item(i)',
      '      Exit For',
      '    End If',
      '  Next',
      'End If',
      'sapi.Speak f.ReadAll',
      'f.Close',
    ].join('\r\n');
    fs.writeFileSync(this.vbsPath, vbs, 'utf8');
  }

  setVoice(name: string): void {
    if (name) {
      fs.writeFileSync(this.voicePath, name, 'utf8');
    } else {
      try { fs.unlinkSync(this.voicePath); } catch { /* ignore */ }
    }
    logger.info(`TTS-Stimme: "${name || 'Standard'}"`);
  }

  getVoices(): string[] {
    const listVbs = [
      'Dim sapi, voices, i',
      'Set sapi = CreateObject("SAPI.SpVoice")',
      'Set voices = sapi.GetVoices()',
      'For i = 0 To voices.Count - 1',
      '  WScript.Echo voices.Item(i).GetDescription()',
      'Next',
    ].join('\r\n');
    const tmpVbs = path.join(os.tmpdir(), 'blitztext-list-voices.vbs');
    fs.writeFileSync(tmpVbs, listVbs, 'utf8');
    const result = spawnSync('cscript.exe', ['//NoLogo', tmpVbs], { encoding: 'utf8', windowsHide: true });
    try { fs.unlinkSync(tmpVbs); } catch { /* ignore */ }
    if (result.error || result.status !== 0) return [];
    return result.stdout.split(/\r?\n/).map((s: string) => s.trim()).filter(Boolean);
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
      .replace(/\r\n/g, '\n')
      .replace(/https?:\/\/\S+/g, '')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/\*{1,2}(.+?)\*{1,2}/g, '$1')
      .replace(/_{1,2}(.+?)_{1,2}/g, '$1')
      .replace(/^[\s]*[-•·▸▹►◆*+]\s+(.+)/gm, '$1. ')
      .replace(/[•·▸▹►◆*]/g, '')
      .replace(/`+/g, '')
      .replace(/\b([A-Za-z]):\\/g, '$1 ')
      .replace(/\\/g, ' ')
      .replace(/\s*\/\s*/g, ' ')
      .replace(/[[\]{}()]/g, '')
      .replace(/[|@~^=<>]/g, ' ')
      .replace(/:(\s*\n)/g, '.$1')
      .replace(/\n{2,}/g, '. ')
      .replace(/\n/g, ' ')
      .replace(/\.\s*\./g, '.')
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
    for (const p of [this.vbsPath, this.txtPath, this.voicePath]) {
      try { fs.unlinkSync(p); } catch { /* ignore */ }
    }
  }
}
