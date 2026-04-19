import {
  app, BrowserWindow, clipboard, globalShortcut, ipcMain, shell, Notification,
} from 'electron';
import * as path from 'path';
import * as fs   from 'fs';

import { TrayManager }        from './tray';
import { OverlayManager }     from './overlay';
import { HotkeyManager }      from './hotkey';
import { AudioRecorder }      from './audio-recorder';
import { WhisperService, stripHallucinations } from './whisper';
import { ModeProcessor }      from './modes';
import { ClipboardManager }   from './clipboard-manager';
import { TtsManager }         from './tts-manager';
import { CorrectionManager }  from './correction-manager';
import { SettingsManager }    from './settings';
import { AppState, Mode, Settings } from './types';
import { logger } from './logger';

// ─── Singleton-Guard ────────────────────────────────────────────────────────
if (!app.requestSingleInstanceLock()) {
  logger.info('Zweite Instanz erkannt – beende.');
  app.quit();
  process.exit(0);
}

// ─── VoiceTyper App ──────────────────────────────────────────────────────────
class VoiceTyper {
  private tray!:      TrayManager;
  private overlay!:   OverlayManager;
  private hotkey!:    HotkeyManager;
  private lastText  = '';
  private recorder!:  AudioRecorder;
  private whisper!:   WhisperService;
  private modes!:     ModeProcessor;
  private clipboard!: ClipboardManager;
  private tts!:         TtsManager;
  private corrections!: CorrectionManager;
  private settings!:   SettingsManager;
  private lastRawTranscript = '';

  private state:       AppState = 'idle';
  private currentMode: Mode     = 'Normal';

  // ─── Init ──────────────────────────────────────────────────────────────────

  async init(): Promise<void> {
    app.setName('Blitztext');
    if (process.platform === 'win32') app.setAppUserModelId('com.blitztext.app');

    // Verhindere, dass der App bei Fenster-Schließen beendet wird (Tray-App)
    app.on('window-all-closed', () => { /* noop – Tray-App läuft weiter */ });

    logger.info(`Blitztext startet. Log: ${logger.getLogPath()}`);

    this.settings = new SettingsManager();
    this.settings.load();

    const s = this.settings.getAll();

    this.modes = new ModeProcessor();
    this.modes.setApiKey(s.apiKey);

    this.whisper = new WhisperService(s.whisperPath, s.whisperModel, s.whisperLanguage);

    this.recorder  = new AudioRecorder();
    this.recorder.init();

    this.clipboard   = new ClipboardManager();
    this.tts         = new TtsManager();
    this.tts.setReplacements(s.ttsReplacements ?? {});
    this.tts.setVoice(s.ttsVoice ?? '');
    this.corrections = new CorrectionManager();

    globalShortcut.register('Ctrl+F9', () => {
      if (this.lastRawTranscript) {
        CorrectionWindow.open(this.lastRawTranscript, this.corrections, this.clipboard);
      }
    });

    this.tray = new TrayManager();
    this.tray.init({
      mode:          this.currentMode,
      onModeChange:  (m) => this.onModeChange(m),
      onSettings:    () => SettingsWindow.open(this.settings, this.recorder, this.whisper),
      onToggleOverlay: () => this.overlay.toggle(),
      onExit:        () => this.quit(),
    });
    this.tray.setMode(this.currentMode);

    this.overlay = new OverlayManager();
    this.overlay.init({
      onModeChange:      (m) => this.onModeChange(m),
      onToggleRecording: () => void this.handleHotkey(),
      onExit:            () => this.quit(),
      onSettings:        () => SettingsWindow.open(this.settings, this.recorder, this.whisper),
      onToggleSpeak:     () => this.handleToggleSpeak(),
      onPauseSpeak:      () => this.handlePauseSpeak(),
    });

    this.hotkey = new HotkeyManager(s.hotkey || 'Ctrl+F8');
    this.hotkey.on('triggered', () => void this.handleHotkey());

    this.setupIPC();

    logger.info('VoiceTyper bereit.');
    this.notify('Blitztext gestartet', `Hotkey: ${s.hotkey || 'Ctrl+F8'} – Modus: ${this.currentMode}`);

    // Whisper-Server im Hintergrund vorwärmen (Modell ins RAM laden)
    this.whisper.warmUp().catch(err =>
      logger.warn('Whisper-Server Warm-Up fehlgeschlagen.', err)
    );
  }

  // ─── IPC ──────────────────────────────────────────────────────────────────

  private setupIPC(): void {
    ipcMain.handle('settings-get', () => this.settings.getAll());

    ipcMain.on('settings-save', (_e, partial: Partial<Settings>) => {
      const prev = this.settings.getAll();
      this.settings.setAll(partial);
      const next = this.settings.getAll();

      if (partial.apiKey !== undefined)   this.modes.setApiKey(next.apiKey);
      if (partial.whisperPath !== undefined) this.whisper.setPath(next.whisperPath);
      if (partial.whisperModel !== undefined) this.whisper.setModel(next.whisperModel);
      if (partial.whisperLanguage !== undefined) this.whisper.setLanguage(next.whisperLanguage);
      if (partial.hotkey && partial.hotkey !== prev.hotkey) {
        this.hotkey.reregister(next.hotkey);
      }
      if (partial.ttsReplacements !== undefined) {
        this.tts.setReplacements(next.ttsReplacements);
      }
      if (partial.ttsVoice !== undefined) {
        this.tts.setVoice(next.ttsVoice);
      }
      logger.info('Settings vom Settings-Fenster übernommen.');
    });

    ipcMain.handle('get-audio-devices',  () => this.recorder.getDevices());
    ipcMain.handle('get-tts-voices',     () => this.tts.getVoices());
    ipcMain.handle('get-corrections',    () => this.corrections.getAll());
    ipcMain.on('delete-correction',  (_e, wrong: string) => this.corrections.remove(wrong));
    ipcMain.on('correction-save', (_e, original: string, corrected: string) => {
      this.corrections.learn(original, corrected);
      this.clipboard.setText(corrected);
      logger.info('Korrekturen gelernt, korrigierter Text in Zwischenablage.');
    });

    ipcMain.handle('check-whisper', (_e, whisperPath: string) => {
      const tmp = new WhisperService(whisperPath);
      return tmp.checkInstallation();
    });

    ipcMain.on('open-log-file', () => {
      const logPath = logger.getLogPath();
      if (logPath && fs.existsSync(logPath)) shell.openPath(logPath);
    });
  }

  // ─── Hotkey-Handler ───────────────────────────────────────────────────────

  private async handleHotkey(): Promise<void> {
    logger.info(`Hotkey – aktueller Zustand: ${this.state}`);

    switch (this.state) {
      case 'idle':       return this.startRecording();
      case 'recording':  return this.stopAndProcess();
      case 'processing':
        logger.info('Hotkey während Verarbeitung ignoriert.');
    }
  }

  private async startRecording(): Promise<void> {
    try {
      this.setState('recording');
      this.recorder.playBeep(880, 80);
      await this.recorder.start(this.settings.get('audioDevice') || undefined);
    } catch (err) {
      logger.error('Aufnahme konnte nicht gestartet werden.', err);
      this.notify('Fehler', `Mikrofon konnte nicht geöffnet werden: ${String(err)}`);
      this.setState('idle');
    }
  }

  private async stopAndProcess(): Promise<void> {
    this.recorder.playBeep(440, 80);
    this.setState('processing');
    try {
      const audio = await this.recorder.stop();

      if (audio.length < 2000) {
        logger.warn('Audio-Buffer zu klein – keine Transkription.');
        this.setState('idle');
        return;
      }

      // RMS-Energiecheck: zu leises Audio → Whisper halluziniert sonst
      const rms = WhisperService.rmsEnergy(audio);
      logger.info(`Audio-RMS: ${rms.toFixed(4)}`);
      if (rms < 0.005) {
        logger.warn(`Audio zu leise (RMS ${rms.toFixed(4)}) – Transkription übersprungen.`);
        this.setState('idle');
        return;
      }

      const transcript = await this.whisper.transcribe(audio);
      if (!transcript.trim()) {
        logger.info('Leeres Transkript – überspringe.');
        this.setState('idle');
        return;
      }

      // Halluzinations-Filter: bekannte Phantom-Phrasen herausschneiden
      const cleaned = stripHallucinations(transcript);
      if (!cleaned) {
        logger.warn(`Nach Halluzinations-Filter leer, verwerfe: "${transcript.trim()}"`);
        this.setState('idle');
        return;
      }
      if (cleaned !== transcript.trim()) {
        logger.info(`Halluzination entfernt: "${transcript.trim()}" → "${cleaned}"`);
      }

      // Korrektur-DB anwenden + Rohtext für Ctrl+F9 merken
      this.lastRawTranscript = cleaned;
      const corrected = this.corrections.correct(cleaned);
      if (corrected !== cleaned) {
        logger.info(`Automatische Korrektur: "${cleaned}" → "${corrected}"`);
      }

      const result = await this.modes.process(corrected, this.currentMode);
      logger.info(`Finaler Text: "${result.slice(0, 100)}"`);

      this.lastText = result.slice(0, 120);
      const textToPaste = /\s$/.test(result) ? result : result + ' ';
      this.clipboard.setText(textToPaste);
      await new Promise(r => setTimeout(r, 300));
      await this.clipboard.simulatePaste();

    } catch (err) {
      logger.error('Verarbeitung fehlgeschlagen.', err);
      this.notify('Fehler', String(err));
    } finally {
      this.setState('idle');
    }
  }

  // ─── Hilfsmethoden ────────────────────────────────────────────────────────

  private onModeChange(mode: Mode): void {
    this.currentMode = mode;
    this.tray.setMode(mode);
    this.overlay?.update(this.state, mode, this.lastText);
    logger.info(`Modus geändert: ${mode}`);
  }

  private setState(state: AppState): void {
    this.state = state;
    this.tray?.setState(state);
    this.overlay?.update(state, this.currentMode, this.lastText);
  }

  private notify(title: string, body: string): void {
    if (Notification.isSupported()) {
      new Notification({ title, body, silent: true }).show();
    }
  }

  private handleToggleSpeak(): void {
    if (this.tts.isSpeaking()) {
      this.tts.stop();
      this.overlay.updateSpeakState('idle');
    } else {
      const text = clipboard.readText();
      if (!text.trim()) {
        logger.info('TTS: Zwischenablage leer.');
        return;
      }
      this.overlay.updateSpeakState('speaking');
      this.tts.speak(text, () => this.overlay.updateSpeakState('idle'));
    }
  }

  private handlePauseSpeak(): void {
    if (this.tts.isPausedState()) {
      this.tts.resume();
      this.overlay.updateSpeakState('speaking');
    } else if (this.tts.isSpeaking()) {
      this.tts.pause();
      this.overlay.updateSpeakState('paused');
    }
  }

  private quit(): void {
    logger.info('App wird beendet.');
    globalShortcut.unregister('Ctrl+F9');
    this.hotkey?.unregister();
    this.recorder?.destroy();
    this.whisper?.destroy();
    this.tts?.destroy();
    this.overlay?.destroy();
    this.tray?.destroy();
    app.quit();
  }
}

// ─── Settings-Fenster ────────────────────────────────────────────────────────

class SettingsWindow {
  private static win: BrowserWindow | null = null;

  static open(
    settings: SettingsManager,
    recorder: AudioRecorder,
    whisper:  WhisperService,
  ): void {
    if (SettingsWindow.win && !SettingsWindow.win.isDestroyed()) {
      SettingsWindow.win.focus();
      return;
    }

    const htmlPath = findRendererFile('settings.html');
    const preloadPath = path.join(__dirname, 'preload.js');

    SettingsWindow.win = new BrowserWindow({
      width:     560,
      height:    680,
      resizable: false,
      title:     'Blitztext – Einstellungen',
      show:      false,
      webPreferences: {
        preload:          fs.existsSync(preloadPath) ? preloadPath : undefined,
        contextIsolation: true,
        nodeIntegration:  false,
      },
    });

    SettingsWindow.win.loadFile(htmlPath);
    SettingsWindow.win.setMenu(null);

    SettingsWindow.win.once('ready-to-show', () => {
      SettingsWindow.win?.show();
      // Sende initiale Settings an den Renderer
      SettingsWindow.win?.webContents.send('settings-init', settings.getAll());
    });

    SettingsWindow.win.on('closed', () => { SettingsWindow.win = null; });
  }
}

// ─── Korrektur-Fenster ───────────────────────────────────────────────────────

class CorrectionWindow {
  private static win: BrowserWindow | null = null;

  static open(original: string, corrections: CorrectionManager, cb: ClipboardManager): void {
    if (CorrectionWindow.win && !CorrectionWindow.win.isDestroyed()) {
      CorrectionWindow.win.focus();
      return;
    }

    CorrectionWindow.win = new BrowserWindow({
      width: 560, height: 400,
      resizable: true,
      title: 'Blitztext – Korrekturen',
      show: false,
      webPreferences: { nodeIntegration: true, contextIsolation: false },
    });

    CorrectionWindow.win.loadFile(findRendererFile('correction.html'));
    CorrectionWindow.win.setMenu(null);

    CorrectionWindow.win.once('ready-to-show', () => {
      CorrectionWindow.win?.show();
      CorrectionWindow.win?.webContents.send('correction-init', original);
    });

    CorrectionWindow.win.on('closed', () => { CorrectionWindow.win = null; });
  }
}

// ─── Hilfsfunktion ──────────────────────────────────────────────────────────

function findRendererFile(filename: string): string {
  const candidates = [
    path.join(__dirname, 'renderer', filename),
    path.join(__dirname, '..', 'src', 'renderer', filename),
  ];
  return candidates.find(p => fs.existsSync(p)) ?? candidates[0];
}

// ─── Einstieg ───────────────────────────────────────────────────────────────

app.whenReady().then(async () => {
  const voiceTyper = new VoiceTyper();
  try {
    await voiceTyper.init();
  } catch (err) {
    logger.error('Kritischer Initialisierungsfehler.', err);
    app.quit();
  }
});


process.on('uncaughtException', (err) => {
  logger.error('Uncaught exception', err);
});
process.on('unhandledRejection', (reason) => {
  logger.error('Unhandled rejection', reason);
});
