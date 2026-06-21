import { spawn, spawnSync, ChildProcess } from 'child_process';
import * as fs   from 'fs';
import * as https from 'https';
import * as os   from 'os';
import * as path from 'path';
import { logger } from './logger';
import { TtsProvider } from './types';

interface AzureTtsConfig {
  key: string;
  region: string;
  voice: string;
}

interface ElevenLabsTtsConfig {
  key: string;
  voiceId: string;
  model: string;
}

export class TtsManager {
  private proc:         ChildProcess | null = null;
  private ps1Path:      string;
  private playerPs1Path: string;
  private txtPath:      string;
  private voicePath:    string;
  private cmdPath:      string;
  private audioPath:    string;
  private elevenLabsAudioPath: string;
  private replacements: Record<string, string> = {};
  private provider:     TtsProvider = 'local';
  private azure:        AzureTtsConfig = { key: '', region: 'westeurope', voice: 'de-DE-KatjaNeural' };
  private elevenLabs:   ElevenLabsTtsConfig = { key: '', voiceId: '', model: 'eleven_multilingual_v2' };
  private _isPaused     = false;

  constructor() {
    this.ps1Path   = path.join(os.tmpdir(), 'blitztext-tts.ps1');
    this.playerPs1Path = path.join(os.tmpdir(), 'blitztext-tts-player.ps1');
    this.txtPath   = path.join(os.tmpdir(), 'blitztext-tts-text.txt');
    this.voicePath = path.join(os.tmpdir(), 'blitztext-tts-voice.txt');
    this.cmdPath   = path.join(os.tmpdir(), 'blitztext-tts-cmd.txt');
    this.audioPath = path.join(os.tmpdir(), 'blitztext-tts.wav');
    this.elevenLabsAudioPath = path.join(os.tmpdir(), 'blitztext-tts-elevenlabs.mp3');
    this.writePs1();
    this.writePlayerPs1();
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

  private writePlayerPs1(): void {
    const ps1 = [
      'param([string]$AudioPath, [string]$CmdPath)',
      'Add-Type -AssemblyName PresentationCore',
      '$player = New-Object System.Windows.Media.MediaPlayer',
      '$player.Open([Uri]::new($AudioPath))',
      '$player.Play()',
      '$lastCmd = ""',
      'while ($true) {',
      '  if (Test-Path $CmdPath) {',
      '    try { $c = (Get-Content -Path $CmdPath -Raw -ErrorAction Stop).Trim() } catch { $c = "" }',
      '    if ($c -ne $lastCmd) {',
      '      $lastCmd = $c',
      '      if ($c -eq "pause") { $player.Pause() }',
      '      elseif ($c -eq "resume") { $player.Play() }',
      '      elseif ($c -eq "stop") { $player.Stop(); break }',
      '    }',
      '  }',
      '  if ($player.NaturalDuration.HasTimeSpan -and $player.Position -ge $player.NaturalDuration.TimeSpan) { break }',
      '  Start-Sleep -Milliseconds 100',
      '}',
      '$player.Close()',
      `Remove-Item -Path "${this.cmdPath}" -Force -ErrorAction SilentlyContinue`,
    ].join('\n');
    fs.writeFileSync(this.playerPs1Path, ps1, 'utf8');
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

  setProvider(provider: TtsProvider): void {
    this.provider = provider;
    logger.info(`TTS-Anbieter: ${provider}`);
  }

  setAzureConfig(config: Partial<AzureTtsConfig>): void {
    this.azure = {
      ...this.azure,
      ...config,
      region: (config.region ?? this.azure.region).trim(),
      voice:  (config.voice  ?? this.azure.voice).trim(),
      key:    (config.key    ?? this.azure.key).trim(),
    };
    logger.info(`Azure TTS konfiguriert: Region=${this.azure.region || 'leer'}, Stimme=${this.azure.voice || 'leer'}, Key=${this.azure.key ? 'gesetzt' : 'leer'}`);
  }

  setElevenLabsConfig(config: Partial<ElevenLabsTtsConfig>): void {
    this.elevenLabs = {
      ...this.elevenLabs,
      ...config,
      key:     (config.key     ?? this.elevenLabs.key).trim(),
      voiceId: (config.voiceId ?? this.elevenLabs.voiceId).trim(),
      model:   (config.model   ?? this.elevenLabs.model).trim(),
    };
    logger.info(`ElevenLabs TTS konfiguriert: Voice-ID=${this.elevenLabs.voiceId ? 'gesetzt' : 'leer'}, Modell=${this.elevenLabs.model || 'leer'}, Key=${this.elevenLabs.key ? 'gesetzt' : 'leer'}`);
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

    if (this.provider === 'elevenlabs' && this.elevenLabs.key && this.elevenLabs.voiceId) {
      void this.speakElevenLabs(cleaned, onDone);
      return;
    }

    if (this.provider === 'azure' && this.azure.key && this.azure.region && this.azure.voice) {
      void this.speakAzure(cleaned, onDone);
      return;
    }

    if (this.provider === 'elevenlabs') {
      logger.warn('ElevenLabs TTS ist ausgewählt, aber API-Key oder Voice-ID fehlt. Fallback wird geprüft.');
      this.fallbackAfterElevenLabs(cleaned, onDone);
      return;
    }

    if (this.provider === 'azure') {
      logger.warn('Azure TTS ist ausgewählt, aber Key/Region/Stimme fehlt. Fallback auf lokale TTS.');
    }
    this.speakLocal(cleaned, onDone);
  }

  private speakLocal(cleaned: string, onDone?: () => void): void {
    fs.writeFileSync(this.txtPath, cleaned, 'utf8');
    try { fs.unlinkSync(this.cmdPath); } catch { /* ignore */ }

    this.proc = spawn('powershell.exe', [
      '-NonInteractive', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', this.ps1Path,
    ], { windowsHide: true, stdio: 'ignore' });
    this.proc.on('exit', () => { this.proc = null; this._isPaused = false; onDone?.(); });

    logger.info(`TTS gestartet: "${cleaned.slice(0, 60)}"`);
  }

  private async speakElevenLabs(cleaned: string, onDone?: () => void): Promise<void> {
    try {
      logger.info(`ElevenLabs TTS gestartet: "${cleaned.slice(0, 60)}"`);
      const audio = await this.synthesizeElevenLabs(cleaned);
      fs.writeFileSync(this.elevenLabsAudioPath, audio);
      try { fs.unlinkSync(this.cmdPath); } catch { /* ignore */ }

      this.proc = spawn('powershell.exe', [
        '-NonInteractive', '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', this.playerPs1Path,
        this.elevenLabsAudioPath,
        this.cmdPath,
      ], { windowsHide: true, stdio: 'ignore' });
      this.proc.on('exit', () => {
        this.proc = null;
        this._isPaused = false;
        onDone?.();
      });
    } catch (err) {
      logger.warn('ElevenLabs TTS fehlgeschlagen. Fallback wird geprüft.', err);
      this.fallbackAfterElevenLabs(cleaned, onDone);
    }
  }

  private fallbackAfterElevenLabs(cleaned: string, onDone?: () => void): void {
    if (this.azure.key && this.azure.region && this.azure.voice) {
      void this.speakAzure(cleaned, onDone);
      return;
    }
    this.speakLocal(cleaned, onDone);
  }

  private async speakAzure(cleaned: string, onDone?: () => void): Promise<void> {
    try {
      logger.info(`Azure TTS gestartet: "${cleaned.slice(0, 60)}"`);
      const audio = await this.synthesizeAzure(cleaned);
      fs.writeFileSync(this.audioPath, audio);
      try { fs.unlinkSync(this.cmdPath); } catch { /* ignore */ }

      this.proc = spawn('powershell.exe', [
        '-NonInteractive', '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', this.playerPs1Path,
        this.audioPath,
        this.cmdPath,
      ], { windowsHide: true, stdio: 'ignore' });
      this.proc.on('exit', () => {
        this.proc = null;
        this._isPaused = false;
        onDone?.();
      });
    } catch (err) {
      logger.warn('Azure TTS fehlgeschlagen, Fallback auf lokale TTS.', err);
      this.speakLocal(cleaned, onDone);
    }
  }

  private synthesizeAzure(text: string): Promise<Buffer> {
    const region = this.azure.region;
    const voice = this.azure.voice;
    const ssml = [
      '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="de-DE">',
      `<voice name="${escapeXml(voice)}">`,
      '<prosody rate="-3%" pitch="0%">',
      escapeXml(text),
      '</prosody>',
      '</voice>',
      '</speak>',
    ].join('');

    const body = Buffer.from(ssml, 'utf8');
    const options: https.RequestOptions = {
      hostname: `${region}.tts.speech.microsoft.com`,
      path: '/cognitiveservices/v1',
      method: 'POST',
      headers: {
        'Ocp-Apim-Subscription-Key': this.azure.key,
        'Content-Type': 'application/ssml+xml',
        'X-Microsoft-OutputFormat': 'riff-24khz-16bit-mono-pcm',
        'User-Agent': 'Blitztext',
        'Content-Length': body.length,
      },
      timeout: 30_000,
    };

    return new Promise((resolve, reject) => {
      const req = https.request(options, (res) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => {
          const payload = Buffer.concat(chunks);
          if (!res.statusCode || res.statusCode < 200 || res.statusCode >= 300) {
            reject(new Error(`Azure TTS HTTP ${res.statusCode}: ${payload.toString('utf8').slice(0, 300)}`));
            return;
          }
          resolve(payload);
        });
      });
      req.on('error', reject);
      req.on('timeout', () => {
        req.destroy(new Error('Azure TTS Timeout'));
      });
      req.write(body);
      req.end();
    });
  }

  private synthesizeElevenLabs(text: string): Promise<Buffer> {
    const voiceId = encodeURIComponent(this.elevenLabs.voiceId);
    const body = Buffer.from(JSON.stringify({
      text,
      model_id: this.elevenLabs.model || 'eleven_multilingual_v2',
      voice_settings: {
        stability: 0.5,
        similarity_boost: 0.75,
      },
    }), 'utf8');

    const options: https.RequestOptions = {
      hostname: 'api.elevenlabs.io',
      path: `/v1/text-to-speech/${voiceId}?output_format=mp3_44100_128`,
      method: 'POST',
      headers: {
        'xi-api-key': this.elevenLabs.key,
        'Accept': 'audio/mpeg',
        'Content-Type': 'application/json',
        'Content-Length': body.length,
      },
      timeout: 30_000,
    };

    return new Promise((resolve, reject) => {
      const req = https.request(options, (res) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => {
          const payload = Buffer.concat(chunks);
          if (!res.statusCode || res.statusCode < 200 || res.statusCode >= 300) {
            reject(new Error(`ElevenLabs TTS HTTP ${res.statusCode}: ${payload.toString('utf8').slice(0, 300)}`));
            return;
          }
          resolve(payload);
        });
      });
      req.on('error', reject);
      req.on('timeout', () => {
        req.destroy(new Error('ElevenLabs TTS Timeout'));
      });
      req.write(body);
      req.end();
    });
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
    for (const p of [this.ps1Path, this.playerPs1Path, this.txtPath, this.voicePath, this.cmdPath, this.audioPath, this.elevenLabsAudioPath]) {
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

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}
