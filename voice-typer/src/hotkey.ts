import { globalShortcut, Notification } from 'electron';
import { EventEmitter }   from 'events';
import { logger }         from './logger';

export declare interface HotkeyManager {
  on(event: 'triggered', listener: () => void): this;
}

export class HotkeyManager extends EventEmitter {
  private currentKey = '';

  constructor(hotkey: string) {
    super();
    this.register(hotkey);
  }

  private register(hotkey: string): void {
    if (this.currentKey) this.unregister();

    let ok = false;
    try {
      ok = globalShortcut.register(hotkey, () => {
        logger.debug(`Hotkey ausgelöst: ${hotkey}`);
        this.emit('triggered');
      });
    } catch (err) {
      logger.error(`Hotkey "${hotkey}" ungültig.`, err);
    }

    if (!ok) {
      logger.warn(`Hotkey "${hotkey}" konnte nicht registriert werden (bereits belegt oder ungültig).`);
      if (Notification.isSupported()) {
        new Notification({
          title: 'Blitztext – Hotkey fehlgeschlagen',
          body:  `Hotkey "${hotkey}" konnte nicht registriert werden. Wähle einen anderen in den Einstellungen.`,
        }).show();
      }
    } else {
      this.currentKey = hotkey;
      logger.info(`Hotkey registriert: ${hotkey}`);
    }
  }

  reregister(newHotkey: string): void {
    this.register(newHotkey);
  }

  unregister(): void {
    if (this.currentKey) {
      globalShortcut.unregister(this.currentKey);
      logger.info(`Hotkey deregistriert: ${this.currentKey}`);
      this.currentKey = '';
    }
  }
}
