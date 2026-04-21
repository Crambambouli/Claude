import { BrowserWindow, ipcMain, screen } from 'electron';
import * as path from 'path';
import * as fs   from 'fs';
import { AppState, Mode } from './types';
import { logger } from './logger';

type SpeakState = 'idle' | 'speaking' | 'paused';

interface OverlayCallbacks {
  onModeChange:       (mode: Mode) => void;
  onToggleRecording:  () => void;
  onExit:             () => void;
  onSettings:         () => void;
  onToggleSpeak:      () => void;
  onPauseSpeak:       () => void;
}

export class OverlayManager {
  private win:       BrowserWindow | null = null;
  private callbacks!: OverlayCallbacks;
  private ipcBound  = false;
  private isCompact = false;
  private readonly FULL_HEIGHT = 270;
  // workArea wird einmalig gecacht (ändert sich nur bei Monitor-Änderungen)
  private workArea  = screen.getPrimaryDisplay().workArea;

  init(callbacks: OverlayCallbacks): void {
    this.callbacks = callbacks;
    this.createWindow();
    this.bindIPC();
  }

  private createWindow(): void {
    const { width, height } = screen.getPrimaryDisplay().workAreaSize;

    this.win = new BrowserWindow({
      width:       300,
      height:      this.FULL_HEIGHT,
      x:           width  - 320,
      y:           height - 260,
      frame:       false,
      resizable:   false,
      alwaysOnTop: true,
      skipTaskbar: true,
      focusable:   false,
      show:        true,
      webPreferences: {
        nodeIntegration:  true,
        contextIsolation: false,
        backgroundThrottling: false,
      },
    });

    const htmlPath = this.findFile('overlay.html');

    this.win.webContents.on('render-process-gone', (_e, details) =>
      logger.error(`Overlay: Renderer abgestürzt – ${details.reason}`));

    // Electron-Bug auf Windows: focusable:false kann intern MA_NOACTIVATEANDEAT
    // setzen, wodurch Maus-Clicks den Renderer nie erreichen.
    // setIgnoreMouseEvents(false) stellt die Maus-Event-Weiterleitung sicher.
    this.win.setIgnoreMouseEvents(false);

    this.win.loadFile(htmlPath);
    this.win.on('closed', () => { this.win = null; });
    logger.info('Overlay-Fenster erstellt.');
  }

  update(state: AppState, mode: Mode, lastText: string): void {
    if (!this.win || this.win.isDestroyed()) return;
    this.win.webContents.send('update-state', { state, mode, lastText });
  }

  updateSpeakState(state: SpeakState): void {
    if (!this.win || this.win.isDestroyed()) return;
    this.win.webContents.send('update-speak-state', state);
  }

  toggle(): void {
    if (!this.win || this.win.isDestroyed()) { this.createWindow(); return; }
    if (this.win.isVisible()) this.win.hide();
    else                      this.win.show();
  }

  isVisible(): boolean {
    return this.win?.isVisible() ?? false;
  }

  destroy(): void {
    this.win?.destroy();
    this.win = null;
  }

  private bindIPC(): void {
    if (this.ipcBound) return;
    this.ipcBound = true;

    ipcMain.on('overlay-set-mode', (_e, mode: Mode) => {
      logger.info(`Overlay: Modus → ${mode}`);
      this.callbacks.onModeChange(mode);
    });

    ipcMain.on('overlay-toggle-recording', () => {
      this.callbacks.onToggleRecording();
    });

    screen.on('display-metrics-changed', () => {
      this.workArea = screen.getPrimaryDisplay().workArea;
    });

    ipcMain.on('overlay-drag-move', (_e, x: number, y: number) => {
      if (!this.win || this.win.isDestroyed()) return;
      const wa = this.workArea;
      const h  = this.isCompact ? 32 : this.FULL_HEIGHT;
      const nx = Math.max(wa.x, Math.min(x, wa.x + wa.width  - 300));
      const ny = Math.max(wa.y, Math.min(y, wa.y + wa.height - h));
      this.win.setPosition(nx, ny);
    });

    ipcMain.on('overlay-hide', () => this.win?.hide());

    ipcMain.on('overlay-minimize', () => {
      if (!this.win || this.win.isDestroyed()) return;
      this.isCompact = !this.isCompact;
      this.win.setResizable(true);
      this.win.setSize(300, this.isCompact ? 32 : this.FULL_HEIGHT);
      this.win.setResizable(false);
      this.win.webContents.send('overlay-compact', this.isCompact);
    });
    ipcMain.on('overlay-pause-speak',  () => this.callbacks.onPauseSpeak());
    ipcMain.on('overlay-exit',          () => this.callbacks.onExit());
    ipcMain.on('overlay-open-settings', () => this.callbacks.onSettings());
    ipcMain.on('overlay-toggle-speak',  () => this.callbacks.onToggleSpeak());
  }

  private findFile(filename: string): string {
    const candidates = [
      path.join(__dirname, 'renderer', filename),
      path.join(__dirname, '..', 'src', 'renderer', filename),
    ];
    return candidates.find(p => fs.existsSync(p)) ?? candidates[0];
  }
}
