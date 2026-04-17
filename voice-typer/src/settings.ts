import * as fs   from 'fs';
import * as path from 'path';
import { app }   from 'electron';
import { DEFAULT_SETTINGS, Settings } from './types';
import { logger } from './logger';

export class SettingsManager {
  private data: Settings = { ...DEFAULT_SETTINGS };
  private filePath = '';

  load(): void {
    this.filePath = path.join(app.getPath('userData'), 'config.json');
    try {
      if (fs.existsSync(this.filePath)) {
        const raw  = fs.readFileSync(this.filePath, 'utf8');
        const parsed = JSON.parse(raw) as Partial<Settings>;
        this.data = { ...DEFAULT_SETTINGS, ...parsed };
        logger.info(`Settings geladen von: ${this.filePath}`);

        // Migration: der nackte F8-Hotkey kollidiert mit WH_KEYBOARD_LL-Hooks
        // anderer Apps und schließt dort Fenster. Automatisch auf Ctrl+F8 heben.
        if (this.data.hotkey === 'F8') {
          this.data.hotkey = 'Ctrl+F8';
          this.save();
          logger.info('Hotkey-Migration: F8 → Ctrl+F8');
        }
      } else {
        logger.info('Keine config.json gefunden, nutze Defaults.');
        this.save(); // Schreibe Defaults für den Nutzer
      }
    } catch (err) {
      logger.warn('Settings konnten nicht geladen werden, nutze Defaults.', err);
      this.data = { ...DEFAULT_SETTINGS };
    }
  }

  save(): void {
    try {
      fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
      fs.writeFileSync(this.filePath, JSON.stringify(this.data, null, 2), 'utf8');
    } catch (err) {
      logger.error('Settings konnten nicht gespeichert werden.', err);
    }
  }

  get<K extends keyof Settings>(key: K): Settings[K] {
    return this.data[key];
  }

  set<K extends keyof Settings>(key: K, value: Settings[K]): void {
    this.data[key] = value;
    this.save();
  }

  getAll(): Settings {
    return { ...this.data };
  }

  setAll(partial: Partial<Settings>): void {
    this.data = { ...this.data, ...partial };
    this.save();
    logger.info('Settings gespeichert.');
  }

  getFilePath(): string { return this.filePath; }
}
