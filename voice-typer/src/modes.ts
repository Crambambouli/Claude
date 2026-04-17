import Anthropic from '@anthropic-ai/sdk';
import { Mode }   from './types';
import { logger } from './logger';

// ─── System-Prompts ──────────────────────────────────────────────────────────

const SYSTEM_PROMPTS: Record<Exclude<Mode, 'Normal'>, string> = {
  Plus: `Du bist ein professioneller Schreibassistent.
Deine Aufgabe: Formuliere diktierten Text klar, präzise und stilistisch hochwertig.
Behalte Inhalt, Sinn und Intention vollständig bei.
Korrigiere Grammatik, Rechtschreibung und Formulierungen.
Antworte NUR mit dem verbesserten Text – ohne Erklärungen, Kommentare oder Anführungszeichen.`,

  Rage: `Du bist ein Deeskalations- und Kommunikationsexperte.
Deine Aufgabe: Wandle aggressive, frustrierte oder unhöfliche Diktate in sachliche,
höfliche und konstruktive Nachrichten um, die denselben Kern-Inhalt transportieren.
Der Ton soll professionell und respektvoll sein.
Antworte NUR mit der umformulierten Nachricht – ohne Erklärungen oder Kommentare.`,

  Emoji: `Du bist ein kreativer Social-Media-Assistent.
Deine Aufgabe: Füge dem gegebenen Text passende, themenbezogene Emojis hinzu,
die Stimmung und Inhalt unterstreichen. Setze Emojis sparsam und gezielt ein (2–6 Stück).
Antworte NUR mit dem Text inklusive Emojis – ohne Erklärungen.`,
};

// ─── ModeProcessor ───────────────────────────────────────────────────────────

export class ModeProcessor {
  private client: Anthropic | null = null;

  setApiKey(key: string): void {
    this.client = key.trim() ? new Anthropic({ apiKey: key.trim() }) : null;
    logger.info(`Claude API Key ${key.trim() ? 'gesetzt' : 'entfernt'}.`);
  }

  async process(text: string, mode: Mode): Promise<string> {
    if (mode === 'Normal') {
      logger.info('Normal-Modus: kein API-Call nötig.');
      return text;
    }

    if (!this.client) {
      throw new Error(
        `Claude API Key nicht konfiguriert.\n` +
        `Bitte in den Einstellungen eintragen, um den Modus "${mode}" zu nutzen.`
      );
    }

    logger.info(`Claude API aufrufen für Modus "${mode}" …`);

    const response = await this.client.messages.create({
      model:      'claude-haiku-4-5-20251001',
      max_tokens: 1024,
      system:     SYSTEM_PROMPTS[mode],
      messages:   [{ role: 'user', content: text }],
    });

    const block = response.content[0];
    if (block.type !== 'text') {
      throw new Error(`Unerwarteter Antwort-Typ von Claude: ${block.type}`);
    }

    const result = block.text.trim();
    logger.info(`Claude Antwort (${mode}): "${result.slice(0, 80)}…"`);
    return result;
  }
}
