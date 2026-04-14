import { BrowserWindow, ipcMain, screen } from 'electron';
import * as path from 'path';
import * as fs   from 'fs';
import { AppState, Mode } from './types';
import { logger } from './logger';

interface OverlayCallbacks {
  onModeChange:       (mode: Mode) => void;
  onToggleRecording:  () => void;
}

export class OverlayManager {
  private win:       BrowserWindow | null = null;
  private callbacks!: OverlayCallbacks;
  private ipcBound  = false;
  private compact   = false;

  private static readonly HEIGHT_NORMAL  = 240;
  private static readonly HEIGHT_COMPACT = 34;

  init(callbacks: OverlayCallbacks): void {
    this.callbacks = callbacks;
    this.createWindow();
    this.bindIPC();
  }

  private createWindow(): void {
    const { width, height } = screen.getPrimaryDisplay().workAreaSize;

    this.win = new BrowserWindow({
      width:       300,
      height:      240,
      x:           width  - 320,
      y:           height - 260,
      frame:       false,
      resizable:   false,
      alwaysOnTop: true,
      skipTaskbar: true,
      show:        true,
      webPreferences: {
        nodeIntegration:  true,
        contextIsolation: false,
        backgroundThrottling: false,
      },
    });

    const htmlPath = this.findFile('overlay.html');
    this.win.loadFile(htmlPath);
    this.win.on('closed', () => { this.win = null; });
    logger.info('Overlay-Fenster erstellt.');
  }

  update(state: AppState, mode: Mode, lastText: string): void {
    if (!this.win || this.win.isDestroyed()) return;
    this.win.webContents.send('update-state', { state, mode, lastText });
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

    ipcMain.on('overlay-hide', () => this.win?.hide());

    ipcMain.on('overlay-toggle-compact', () => {
      if (!this.win || this.win.isDestroyed()) return;
      this.compact = !this.compact;
      const h = this.compact ? OverlayManager.HEIGHT_COMPACT : OverlayManager.HEIGHT_NORMAL;
      this.win.setSize(300, h, true);   // true = animate
      this.win.webContents.send('set-compact', this.compact);
    });
  }

  private findFile(filename: string): string {
    const candidates = [
      path.join(__dirname, 'renderer', filename),
      path.join(__dirname, '..', 'src', 'renderer', filename),
    ];
    return candidates.find(p => fs.existsSync(p)) ?? candidates[0];
  }
}
