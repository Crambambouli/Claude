/**
 * Preload-Skript für das Settings-Fenster.
 * Exponiert sichere IPC-Brücke über contextBridge.
 */
import { contextBridge, ipcRenderer } from 'electron';
import { Settings, AudioDevice } from './types';

contextBridge.exposeInMainWorld('electronAPI', {
  /** Lädt alle gespeicherten Einstellungen. */
  getSettings: (): Promise<Settings> =>
    ipcRenderer.invoke('settings-get'),

  /** Speichert Einstellungen. */
  saveSettings: (settings: Partial<Settings>): void =>
    ipcRenderer.send('settings-save', settings),

  /** Gibt verfügbare Audio-Eingabegeräte zurück. */
  getAudioDevices: (): Promise<AudioDevice[]> =>
    ipcRenderer.invoke('get-audio-devices'),

  /** Registriert den onSettingsInit-Callback (einmalig beim Öffnen). */
  onSettingsInit: (cb: (s: Settings) => void): void => {
    ipcRenderer.once('settings-init', (_e, s: Settings) => cb(s));
  },

  /** Prüft, ob Whisper installiert ist. */
  checkWhisper: (whisperPath: string): Promise<{ ok: boolean; message: string }> =>
    ipcRenderer.invoke('check-whisper', whisperPath),

  /** Gibt verfügbare SAPI-TTS-Stimmen zurück. */
  getTtsVoices: (): Promise<string[]> =>
    ipcRenderer.invoke('get-tts-voices'),

  /** Öffnet Log-Datei im System-Editor. */
  openLogFile: (): void =>
    ipcRenderer.send('open-log-file'),
});
