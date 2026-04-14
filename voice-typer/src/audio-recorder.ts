import { BrowserWindow, ipcMain, session } from 'electron';
import * as path from 'path';
import * as fs   from 'fs';
import { AudioDevice } from './types';
import { logger } from './logger';

/**
 * Verwaltet ein verstecktes BrowserWindow, das die MediaRecorder/Web-Audio-API
 * nutzt, um Mikrofon-Audio als 16-bit-WAV aufzuzeichnen.
 */
export class AudioRecorder {
  private win: BrowserWindow | null = null;
  private resolveStop: ((buf: Buffer) => void) | null = null;
  private rejectStop:  ((err: Error) => void) | null  = null;
  private isRecording = false;
  private recorderHtmlPath = '';

  init(): void {
    // Pfad zur recorder.html (dist/renderer/ nach Build; src/renderer/ im Dev)
    const candidates = [
      path.join(__dirname, 'renderer', 'recorder.html'),
      path.join(__dirname, '..', 'src', 'renderer', 'recorder.html'),
    ];
    this.recorderHtmlPath = candidates.find(p => fs.existsSync(p)) ?? candidates[0];

    // Mikrofon-Zugriff automatisch erlauben
    session.defaultSession.setPermissionRequestHandler((_wc, perm, cb) => {
      cb(perm === 'media');
    });
    session.defaultSession.setPermissionCheckHandler((_wc, perm) => {
      return perm === 'media';
    });

    this.createWindow();
    this.setupIPC();
    logger.info(`AudioRecorder initialisiert. HTML: ${this.recorderHtmlPath}`);
  }

  private createWindow(): void {
    this.win = new BrowserWindow({
      show:   false,
      width:  1,
      height: 1,
      skipTaskbar: true,
      webPreferences: {
        nodeIntegration:    true,
        contextIsolation:   false,
        backgroundThrottling: false,
      },
    });
    this.win.loadFile(this.recorderHtmlPath);
    this.win.webContents.on('did-fail-load', (_e, code, desc) => {
      logger.error(`Recorder-Fenster konnte nicht laden: ${desc} (${code})`);
    });
    // Crash-Wiederherstellung
    this.win.webContents.on('render-process-gone', (_e, details) => {
      logger.warn(`Renderer abgestürzt: ${details.reason}. Neustart…`);
      this.createWindow();
    });
  }

  private setupIPC(): void {
    ipcMain.on('recorder-started', () => {
      logger.info('Aufnahme gestartet (Renderer bestätigt).');
    });

    ipcMain.on('recorder-error', (_e, msg: string) => {
      logger.error(`Recorder-Fehler: ${msg}`);
      if (this.rejectStop) {
        this.rejectStop(new Error(`Mikrofon-Fehler: ${msg}`));
        this.resolveStop = null;
        this.rejectStop  = null;
      }
      this.isRecording = false;
    });

    ipcMain.on('recording-complete', (_e, data: Buffer | Uint8Array) => {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      logger.info(`Aufnahme abgeschlossen. Puffergröße: ${buf.length} Bytes`);
      if (this.resolveStop) {
        this.resolveStop(buf);
        this.resolveStop = null;
        this.rejectStop  = null;
      }
      this.isRecording = false;
    });

    ipcMain.on('recording-silence-timeout', () => {
      logger.info('Stille-Timeout erreicht – Aufnahme automatisch gestoppt.');
    });
  }

  start(deviceId?: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.isRecording) {
        reject(new Error('Aufnahme läuft bereits.'));
        return;
      }
      if (!this.win || this.win.isDestroyed()) this.createWindow();

      this.isRecording = true;

      // Warte kurz bis Fenster bereit
      const send = () => {
        this.win?.webContents.send('start-recording', deviceId ?? null);
        logger.info(`start-recording gesendet (device: ${deviceId ?? 'default'})`);
        resolve();
      };

      if (this.win!.webContents.isLoading()) {
        this.win!.webContents.once('did-finish-load', send);
      } else {
        send();
      }
    });
  }

  stop(): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      if (!this.isRecording) {
        reject(new Error('Keine aktive Aufnahme.'));
        return;
      }
      this.resolveStop = resolve;
      this.rejectStop  = reject;
      this.win?.webContents.send('stop-recording');
      logger.info('stop-recording gesendet.');

      // Sicherheits-Timeout: wenn Renderer nie antwortet
      setTimeout(() => {
        if (this.resolveStop) {
          reject(new Error('Aufnahme-Timeout: Renderer hat nicht geantwortet.'));
          this.resolveStop = null;
          this.rejectStop  = null;
          this.isRecording = false;
        }
      }, 10_000);
    });
  }

  /** Gibt verfügbare Audio-Eingabegeräte zurück (aus dem Renderer). */
  getDevices(): Promise<AudioDevice[]> {
    return new Promise((resolve) => {
      if (!this.win || this.win.isDestroyed()) {
        resolve([]);
        return;
      }
      ipcMain.once('audio-devices-result', (_e, devices: AudioDevice[]) => resolve(devices));
      this.win.webContents.send('get-audio-devices');
      setTimeout(() => resolve([]), 3000);
    });
  }

  destroy(): void {
    if (this.win && !this.win.isDestroyed()) {
      this.win.destroy();
      this.win = null;
    }
  }
}
