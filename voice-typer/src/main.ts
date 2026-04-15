import {
  app, BrowserWindow, ipcMain, shell, Notification,
} from 'electron';
import * as path from 'path';
import * as fs   from 'fs';
import { exec }  from 'child_process';

import { TrayManager }       from './tray';
import { OverlayManager }    from './overlay';
import { HotkeyManager }     from './hotkey';
import { AudioRecorder }     from './audio-recorder';
import { WhisperService, stripHallucinations } from './whisper';
import { ModeProcessor }     from './modes';
import { ClipboardManager }  from './clipboard-manager';
import { SettingsManager }   from './settings';
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
  private settings!:  SettingsManager;

  private state:       AppState = 'idle';
  private currentMode: Mode     = 'Normal';

  // ─── Init ──────────────────────────────────────────────────────────────────

  async init(): Promise<void> {
    app.setName('Voice Typer');
    if (process.platform === 'win32') app.setAppUserModelId('com.voicetyper.app');

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

    this.clipboard = new ClipboardManager();

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
    });

    this.hotkey = new HotkeyManager(s.hotkey || 'F8');
    this.hotkey.on('triggered', () => void this.handleHotkey());

    this.setupIPC();

    logger.info('VoiceTyper bereit.');
    this.notify('Voice Typer gestartet', `Hotkey: ${s.hotkey || 'F8'} – Modus: ${this.currentMode}`);

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
      logger.info('Settings vom Settings-Fenster übernommen.');
    });

    ipcMain.handle('get-audio-devices', () => this.recorder.getDevices());

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
      beep(880, 80);   // hoher Ton = Aufnahme START
      await this.recorder.start(this.settings.get('audioDevice') || undefined);
    } catch (err) {
      logger.error('Aufnahme konnte nicht gestartet werden.', err);
      this.notify('Fehler', `Mikrofon konnte nicht geöffnet werden: ${String(err)}`);
      this.setState('idle');
    }
  }

  private async stopAndProcess(): Promise<void> {
    beep(440, 80);   // tiefer Ton = Aufnahme STOP
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

      const result = await this.modes.process(cleaned, this.currentMode);
      logger.info(`Finaler Text: "${result.slice(0, 100)}"`);

      this.lastText = result.slice(0, 120);
      this.clipboard.setText(result);
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

  private quit(): void {
    logger.info('App wird beendet.');
    this.hotkey?.unregister();
    this.recorder?.destroy();
    this.whisper?.destroy();
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
      height:    560,
      resizable: false,
      title:     'Voice Typer – Einstellungen',
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

// ─── Audio-Feedback ──────────────────────────────────────────────────────────
function beep(freq: number, durationMs: number): void {
  if (process.platform === 'win32') {
    exec(`powershell -Command "[console]::beep(${freq},${durationMs})"`,
      { windowsHide: true },
      (err) => { if (err) logger.debug('Beep fehlgeschlagen: ' + err.message); }
    );
  }
}

process.on('uncaughtException', (err) => {
  logger.error('Uncaught exception', err);
});
process.on('unhandledRejection', (reason) => {
  logger.error('Unhandled rejection', reason);
});
