import * as fs from 'fs';
import * as path from 'path';

type Translations = Record<string, string>;
let current: Translations = {};

export function loadLanguage(lang: string): void {
  // __dirname ist bei dist/i18n/index.js bereits der i18n-Ordner
  const file = path.join(__dirname, `${lang}.json`);
  const fallback = path.join(__dirname, 'de.json');
  try {
    current = JSON.parse(fs.readFileSync(fs.existsSync(file) ? file : fallback, 'utf8'));
  } catch {
    current = {};
  }
}

export function t(key: string, vars?: Record<string, string>): string {
  let s = current[key] ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, v);
  return s;
}

export function getAllTranslations(): Translations {
  return { ...current };
}
