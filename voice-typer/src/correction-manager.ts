import * as fs   from 'fs';
import * as path from 'path';
import { app }   from 'electron';
import { logger } from './logger';

interface CorrectionEntry {
  corrected: string;
  count:     number;
  lastUsed:  string;
}

type CorrectionDB = Record<string, CorrectionEntry>;

export class CorrectionManager {
  private db:     CorrectionDB = {};
  private dbPath: string;

  constructor() {
    this.dbPath = path.join(app.getPath('userData'), 'corrections.json');
    this.load();
  }

  private load(): void {
    try {
      if (fs.existsSync(this.dbPath)) {
        this.db = JSON.parse(fs.readFileSync(this.dbPath, 'utf8'));
        logger.info(`Corrections-DB geladen: ${Object.keys(this.db).length} Einträge.`);
      }
    } catch (err) {
      logger.warn('Corrections-DB konnte nicht geladen werden.', err);
      this.db = {};
    }
  }

  private save(): void {
    try {
      fs.writeFileSync(this.dbPath, JSON.stringify(this.db, null, 2), 'utf8');
    } catch (err) {
      logger.error('Corrections-DB konnte nicht gespeichert werden.', err);
    }
  }

  correct(text: string): string {
    let result = text;
    for (const [wrong, entry] of Object.entries(this.db)) {
      result = result.replace(
        new RegExp(`\\b${escapeRegex(wrong)}\\b`, 'gi'),
        entry.corrected,
      );
    }
    return result;
  }

  learn(original: string, corrected: string): void {
    const pairs = diffWords(original, corrected);
    let changed = false;
    for (const [wrong, right] of Object.entries(pairs)) {
      if (!wrong || !right || wrong.toLowerCase() === right.toLowerCase()) continue;
      const now = new Date().toISOString();
      if (this.db[wrong]) {
        this.db[wrong].corrected = right;
        this.db[wrong].count++;
        this.db[wrong].lastUsed = now;
      } else {
        this.db[wrong] = { corrected: right, count: 1, lastUsed: now };
      }
      logger.info(`Korrektur gelernt: "${wrong}" → "${right}"`);
      changed = true;
    }
    if (changed) this.save();
  }

  getAll(): Record<string, string> {
    return Object.fromEntries(
      Object.entries(this.db).map(([k, v]) => [k, v.corrected]),
    );
  }

  remove(wrong: string): void {
    delete this.db[wrong];
    this.save();
    logger.info(`Korrektur entfernt: "${wrong}"`);
  }
}

// ─── Hilfsfunktionen ────────────────────────────────────────────────────────

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function diffWords(original: string, corrected: string): Record<string, string> {
  const a = original.trim().split(/\s+/);
  const b = corrected.trim().split(/\s+/);
  const dp = lcsMatrix(a, b);
  const pairs: Record<string, string> = {};
  extractPairs(a, b, dp, pairs);
  return pairs;
}

function lcsMatrix(a: string[], b: string[]): number[][] {
  const m = a.length, n = b.length;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i-1].toLowerCase() === b[j-1].toLowerCase()
        ? dp[i-1][j-1] + 1
        : Math.max(dp[i-1][j], dp[i][j-1]);
    }
  }
  return dp;
}

function extractPairs(
  a: string[], b: string[],
  dp: number[][],
  pairs: Record<string, string>,
): void {
  let i = a.length, j = b.length;
  const aChunk: string[] = [], bChunk: string[] = [];

  const flush = () => {
    const aStr = aChunk.reverse().join(' ');
    const bStr = bChunk.reverse().join(' ');
    if (aStr && bStr && aStr.toLowerCase() !== bStr.toLowerCase()) pairs[aStr] = bStr;
    aChunk.length = 0;
    bChunk.length = 0;
  };

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i-1].toLowerCase() === b[j-1].toLowerCase()) {
      flush();
      i--; j--;
    } else if (j > 0 && (i === 0 || dp[i][j-1] >= dp[i-1][j])) {
      bChunk.push(b[--j]);
    } else {
      aChunk.push(a[--i]);
    }
  }
  flush();
}
