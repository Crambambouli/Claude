// ─── Shared TypeScript Types ────────────────────────────────────────────────

export type Mode      = 'Normal' | 'Plus' | 'Rage' | 'Emoji';
export type AppState  = 'idle' | 'recording' | 'processing';

export interface Settings {
  /** Pfad zum whisper-CLI-Binary / Verzeichnis. Leer = PATH-Suche */
  whisperPath: string;
  /** Whisper-Modell, z.B. "base", "small", "medium" */
  whisperModel: string;
  /** Sprache für Whisper, "auto" = automatisch */
  whisperLanguage: string;
  /** Anthropic-API-Schlüssel für Modi Plus/Rage/Emoji */
  apiKey: string;
  /** Globaler Hotkey, z.B. "F8" */
  hotkey: string;
  /** Geräte-ID des Mikrofons (leer = Standard) */
  audioDevice: string;
}

export const DEFAULT_SETTINGS: Settings = {
  whisperPath:     '',
  whisperModel:    'base',
  whisperLanguage: 'auto',
  apiKey:          '',
  hotkey:          'Ctrl+F8',
  audioDevice:     '',
};

export interface AudioDevice {
  id:    string;
  label: string;
}

export interface WindowInfo {
  title:       string;
  processName: string;
  hwnd:        string;
}
