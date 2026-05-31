import * as fs from 'fs';
import * as path from 'path';
import { app } from 'electron';
import { DEFAULT_SETTINGS, Settings } from './types';
import { logger } from './logger';

/** Aktuelle Config-Schema-Version (steuert einmalige Default-Migrationen). */
const SCHEMA_VERSION = 1;

export class SettingsManager {
  private data: Settings = { ...DEFAULT_SETTINGS };
  private filePath = '';

  load(): void {
    this.filePath = path.join(app.getPath('userData'), 'config.json');
    try {
      if (fs.existsSync(this.filePath)) {
        const raw = fs.readFileSync(this.filePath, 'utf8');
        const parsed = JSON.parse(raw) as Partial<Settings> & {
          _schemaVersion?: number;
        };
        this.data = { ...DEFAULT_SETTINGS, ...parsed };
        logger.info(`Settings geladen von: ${this.filePath}`);

        // Migration: der nackte F8-Hotkey kollidiert mit WH_KEYBOARD_LL-Hooks
        // anderer Apps und schließt dort Fenster. Automatisch auf Ctrl+F8 heben.
        if (this.data.hotkey === 'F8') {
          this.data.hotkey = 'Ctrl+F8';
          logger.info('Hotkey-Migration: F8 → Ctrl+F8');
        }

        // Einmalige Migration (über _schemaVersion gegated): alte Schwachstellen-
        // Defaults base/auto auf die neuen, besseren Defaults heben. Eine spätere
        // bewusste Nutzerwahl (z.B. wieder "base") bleibt dadurch erhalten.
        if ((parsed._schemaVersion ?? 0) < 1) {
          if (this.data.whisperModel === 'base') {
            this.data.whisperModel = DEFAULT_SETTINGS.whisperModel;
            logger.info(
              `Whisper-Modell-Migration: base → ${DEFAULT_SETTINGS.whisperModel}`,
            );
          }
          if (this.data.whisperLanguage === 'auto') {
            this.data.whisperLanguage = DEFAULT_SETTINGS.whisperLanguage;
            logger.info(
              `Whisper-Sprache-Migration: auto → ${DEFAULT_SETTINGS.whisperLanguage}`,
            );
          }
        }

        this.save();
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
      const out = { ...this.data, _schemaVersion: SCHEMA_VERSION };
      fs.writeFileSync(this.filePath, JSON.stringify(out, null, 2), 'utf8');
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

  getFilePath(): string {
    return this.filePath;
  }
}
