import { globalShortcut } from 'electron';
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

    const ok = globalShortcut.register(hotkey, () => {
      logger.debug(`Hotkey ausgelöst: ${hotkey}`);
      this.emit('triggered');
    });

    if (!ok) {
      logger.warn(`Hotkey "${hotkey}" konnte nicht registriert werden (bereits belegt?).`);
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
