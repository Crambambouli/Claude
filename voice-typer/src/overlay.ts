import { BrowserWindow, ipcMain, screen, app, powerMonitor } from 'electron';
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
  private win:          BrowserWindow | null = null;
  private callbacks!:   OverlayCallbacks;
  private ipcBound    = false;
  private isCompact   = false;
  private readonly FULL_HEIGHT = 270;
  private workArea    = screen.getPrimaryDisplay().workArea;
  private positionPath = '';
  // Diagnose Overlay-Einfrieren: Herzschlag-Prüfung des Renderers
  private lastPongAt   = Date.now();
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private rendererStuck = false;

  init(callbacks: OverlayCallbacks): void {
    this.positionPath = path.join(app.getPath('userData'), 'overlay-position.json');
    this.callbacks = callbacks;
    this.createWindow();
    this.bindIPC();
    this.bindDiagnostics();
  }

  /**
   * Gegenmaßnahme Overlay-Einfrieren: Maus-Weiterleitung und „immer im
   * Vordergrund" erneut setzen. Wird bei Anzeigen, Statuswechsel, Aufwachen,
   * Entsperren und Monitoränderungen aufgerufen. Ändert nichts am Fokus
   * (focusable:false bleibt, damit Strg+V im Zielfenster landet).
   */
  private reassertInteractive(): void {
    if (!this.win || this.win.isDestroyed()) return;
    this.win.setIgnoreMouseEvents(false);
    this.win.setAlwaysOnTop(true);
  }

  private bindDiagnostics(): void {
    const ev = (name: string) => () => {
      logger.info(`Overlay-Diagnose: ${name}`);
      this.reassertInteractive();
    };
    powerMonitor.on('suspend',       () => logger.info('Overlay-Diagnose: Ruhezustand beginnt'));
    powerMonitor.on('resume',        ev('Aufgewacht aus Ruhezustand'));
    powerMonitor.on('lock-screen',   () => logger.info('Overlay-Diagnose: Bildschirm gesperrt'));
    powerMonitor.on('unlock-screen', ev('Bildschirm entsperrt'));
    screen.on('display-added',       ev('Monitor hinzugefügt'));
    screen.on('display-removed',     ev('Monitor entfernt'));

    // Herzschlag: alle 60 s prüfen, ob der Overlay-Renderer noch antwortet.
    // Geloggt wird nur bei Ausfall und bei Wiederkehr.
    ipcMain.on('overlay-mouse-down', (_e, target: string) =>
      logger.info(`Overlay-Diagnose: Klick angekommen (${String(target).slice(0, 40)})`));

    ipcMain.on('overlay-pong', () => {
      this.lastPongAt = Date.now();
      if (this.rendererStuck) {
        this.rendererStuck = false;
        logger.info('Overlay-Diagnose: Renderer antwortet wieder');
      }
    });
    this.heartbeatTimer = setInterval(() => {
      if (!this.win || this.win.isDestroyed()) return;
      const silentMs = Date.now() - this.lastPongAt;
      if (silentMs > 90_000 && !this.rendererStuck) {
        this.rendererStuck = true;
        logger.warn(`Overlay-Diagnose: Renderer antwortet seit ${Math.round(silentMs / 1000)} s nicht ` +
          `(sichtbar=${this.win.isVisible()}, vordergrund=${this.win.isAlwaysOnTop()})`);
      }
      this.win.webContents.send('overlay-ping');
    }, 60_000);
  }

  private loadSavedPosition(): { x: number; y: number } | null {
    try {
      if (fs.existsSync(this.positionPath)) {
        const data = JSON.parse(fs.readFileSync(this.positionPath, 'utf8')) as { x: unknown; y: unknown };
        if (typeof data.x === 'number' && typeof data.y === 'number') {
          // Sicherstellen dass die Position noch auf einem vorhandenen Monitor liegt
          const display = screen.getDisplayNearestPoint({ x: data.x, y: data.y });
          const wa = display.workArea;
          if (data.x >= wa.x && data.x < wa.x + wa.width &&
              data.y >= wa.y && data.y < wa.y + wa.height) {
            return { x: data.x, y: data.y };
          }
        }
      }
    } catch { /* ignore */ }
    return null;
  }

  private savePosition(x: number, y: number): void {
    try {
      fs.writeFileSync(this.positionPath, JSON.stringify({ x, y }), 'utf8');
    } catch { /* ignore */ }
  }

  private createWindow(): void {
    const { width, height } = screen.getPrimaryDisplay().workAreaSize;
    const saved = this.loadSavedPosition();
    const startX = saved ? saved.x : width  - 320;
    const startY = saved ? saved.y : height - 260;

    this.win = new BrowserWindow({
      width:       300,
      height:      this.FULL_HEIGHT,
      x:           startX,
      y:           startY,
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
    this.win.on('unresponsive', () => logger.warn('Overlay-Diagnose: Fenster reagiert nicht (unresponsive)'));
    this.win.on('responsive',   () => logger.info('Overlay-Diagnose: Fenster reagiert wieder (responsive)'));
    this.win.on('always-on-top-changed', (_e, isOnTop) =>
      logger.info(`Overlay-Diagnose: immer-im-Vordergrund geändert → ${isOnTop}`));

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
    this.reassertInteractive();
    this.win.webContents.send('update-state', { state, mode, lastText });
  }

  updateSpeakState(state: SpeakState): void {
    if (!this.win || this.win.isDestroyed()) return;
    this.win.webContents.send('update-speak-state', state);
  }

  toggle(): void {
    if (!this.win || this.win.isDestroyed()) { this.createWindow(); return; }
    if (this.win.isVisible()) this.win.hide();
    else                      { this.win.show(); this.reassertInteractive(); }
  }

  isVisible(): boolean {
    return this.win?.isVisible() ?? false;
  }

  destroy(): void {
    if (this.heartbeatTimer) { clearInterval(this.heartbeatTimer); this.heartbeatTimer = null; }
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

    // Position erst beim Loslassen der Maus speichern (nicht bei jedem Bild)
    ipcMain.on('overlay-drag-end', () => {
      if (!this.win || this.win.isDestroyed()) return;
      const [x, y] = this.win.getPosition();
      this.savePosition(x, y);
    });

    ipcMain.on('overlay-hide', () => this.win?.hide());

    ipcMain.on('overlay-minimize', () => {
      if (!this.win || this.win.isDestroyed()) return;
      this.isCompact = !this.isCompact;
      this.win.setResizable(true);
      this.win.setSize(300, this.isCompact ? 32 : this.FULL_HEIGHT);
      this.win.setResizable(false);
      this.reassertInteractive();
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
