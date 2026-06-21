// ─── Shared TypeScript Types ────────────────────────────────────────────────

export type Mode      = 'Normal' | 'Plus' | 'Rage' | 'Emoji';
export type AppState  = 'idle' | 'recording' | 'processing';
export type TtsProvider = 'local' | 'azure' | 'elevenlabs';

export interface Settings {
  /** Reserviert – wird von whisper.cpp nicht ausgewertet (Binary-Pfad ist fest). Leer lassen. */
  whisperPath: string;
  /** Whisper-Modell-Kennung, z.B. "large-v3-q5_0" (whisper.cpp ggml-Format) */
  whisperModel: string;
  /** Sprache für Whisper, "auto" = automatisch */
  whisperLanguage: string;
  /** Anthropic-API-Schlüssel für Modi Plus/Rage/Emoji */
  apiKey: string;
  /** Globaler Hotkey, z.B. "Ctrl+F8" */
  hotkey: string;
  /** Geräte-ID des Mikrofons (leer = Standard) */
  audioDevice: string;
  /** TTS-Aussprache-Ersetzungen: englischer Begriff → phonetisches Deutsch */
  ttsReplacements: Record<string, string>;
  /** Name der SAPI-Stimme (leer = Systemstandard) */
  ttsVoice: string;
  /** TTS-Anbieter: lokale Windows-Stimme oder Azure Speech */
  ttsProvider: TtsProvider;
  /** Azure Speech Region, z.B. "westeurope" oder "germanywestcentral" */
  azureSpeechRegion: string;
  /** Azure Neural Voice, z.B. "de-DE-KatjaNeural" */
  azureSpeechVoice: string;
  /** ElevenLabs Voice-ID */
  elevenLabsVoiceId: string;
  /** ElevenLabs Modell, z.B. "eleven_multilingual_v2" */
  elevenLabsModel: string;
}

export const DEFAULT_SETTINGS: Settings = {
  whisperPath:     '',
  whisperModel:    'medium',
  whisperLanguage: 'de',
  apiKey:          '',
  hotkey:          'Ctrl+F8',
  audioDevice:     '',
  ttsVoice:        '',
  ttsProvider:     'local',
  azureSpeechRegion: 'westeurope',
  azureSpeechVoice:  'de-DE-KatjaNeural',
  elevenLabsVoiceId: '',
  elevenLabsModel:   'eleven_multilingual_v2',
  ttsReplacements: {
    Layer:    'Lejer',
    Layers:   'Lejerz',
    Memory:   'Memori',
    Wiki:     'Wicki',
    Session:  'Seschön',
    Prompt:   'Prommt',
    Prompts:  'Prommts',
    Token:    'Touken',
    Tokens:   'Toukens',
  },
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
