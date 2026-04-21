import { spawn, spawnSync, ChildProcess } from 'child_process';
import * as fs   from 'fs';
import * as os   from 'os';
import * as path from 'path';
import { logger } from './logger';

export class TtsManager {
  private proc:         ChildProcess | null = null;
  private ps1Path:      string;
  private txtPath:      string;
  private voicePath:    string;
  private cmdPath:      string;
  private replacements: Record<string, string> = {};
  private _isPaused     = false;

  constructor() {
    this.ps1Path   = path.join(os.tmpdir(), 'blitztext-tts.ps1');
    this.txtPath   = path.join(os.tmpdir(), 'blitztext-tts-text.txt');
    this.voicePath = path.join(os.tmpdir(), 'blitztext-tts-voice.txt');
    this.cmdPath   = path.join(os.tmpdir(), 'blitztext-tts-cmd.txt');
    this.writePs1();
  }

  private writePs1(): void {
    // System.Speech.Synthesis supports Pause()/Resume() – VBScript/wscript does not.
    // The script polls a command file every 100 ms to react to pause/resume requests.
    const ps1 = [
      'Add-Type -AssemblyName System.Speech',
      '$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer',
      `if (Test-Path "${this.voicePath}") {`,
      `  try { $n = (Get-Content -Path "${this.voicePath}" -Raw).Trim(); if ($n) { $synth.SelectVoice($n) } } catch {}`,
      '}',
      `$text = Get-Content -Path "${this.txtPath}" -Raw -Encoding UTF8`,
      '$synth.SpeakAsync($text) | Out-Null',
      '$lastCmd = ""',
      'while ($synth.State -ne [System.Speech.Synthesis.SynthesizerState]::Ready) {',
      `  if (Test-Path "${this.cmdPath}") {`,
      `    try { $c = (Get-Content -Path "${this.cmdPath}" -Raw -ErrorAction Stop).Trim() } catch { $c = "" }`,
      '    if ($c -ne $lastCmd) {',
      '      $lastCmd = $c',
      '      if ($c -eq "pause")  { $synth.Pause() }',
      '      elseif ($c -eq "resume") { $synth.Resume() }',
      '      elseif ($c -eq "stop")   { $synth.SpeakAsyncCancelAll(); break }',
      '    }',
      '  }',
      '  Start-Sleep -Milliseconds 100',
      '}',
      '$synth.Dispose()',
      `Remove-Item -Path "${this.cmdPath}" -Force -ErrorAction SilentlyContinue`,
    ].join('\n');
    fs.writeFileSync(this.ps1Path, ps1, 'utf8');
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
    const ps1 = [
      'Add-Type -AssemblyName System.Speech',
      '$s = New-Object System.Speech.Synthesis.SpeechSynthesizer',
      '$s.GetInstalledVoices() | ForEach-Object { Write-Output $_.VoiceInfo.Name }',
      '$s.Dispose()',
    ].join('\n');
    const tmpPs1 = path.join(os.tmpdir(), 'blitztext-list-voices.ps1');
    fs.writeFileSync(tmpPs1, ps1, 'utf8');
    const result = spawnSync('powershell.exe', [
      '-NonInteractive', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', tmpPs1,
    ], { encoding: 'utf8', windowsHide: true });
    try { fs.unlinkSync(tmpPs1); } catch { /* ignore */ }
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

    fs.writeFileSync(this.txtPath, cleaned, 'utf8');
    try { fs.unlinkSync(this.cmdPath); } catch { /* ignore */ }

    this.proc = spawn('powershell.exe', [
      '-NonInteractive', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', this.ps1Path,
    ], { windowsHide: true, stdio: 'ignore' });
    this.proc.on('exit', () => { this.proc = null; this._isPaused = false; onDone?.(); });

    logger.info(`TTS gestartet: "${cleaned.slice(0, 60)}"`);
  }

  pause(): void {
    if (this.proc && !this._isPaused) {
      fs.writeFileSync(this.cmdPath, 'pause', 'utf8');
      this._isPaused = true;
      logger.info('TTS pausiert.');
    }
  }

  resume(): void {
    if (this.proc && this._isPaused) {
      fs.writeFileSync(this.cmdPath, 'resume', 'utf8');
      this._isPaused = false;
      logger.info('TTS fortgesetzt.');
    }
  }

  stop(): void {
    if (this.proc) {
      this.proc.kill();
      this.proc = null;
      this._isPaused = false;
      try { fs.unlinkSync(this.cmdPath); } catch { /* ignore */ }
      logger.info('TTS gestoppt.');
    }
  }

  isSpeaking(): boolean {
    return this.proc !== null;
  }

  isPausedState(): boolean {
    return this._isPaused;
  }

  destroy(): void {
    this.stop();
    for (const p of [this.ps1Path, this.txtPath, this.voicePath, this.cmdPath]) {
      try { fs.unlinkSync(p); } catch { /* ignore */ }
    }
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
}
