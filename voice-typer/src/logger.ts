import * as fs   from 'fs';
import * as path from 'path';
import { app }   from 'electron';

type Level = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

class Logger {
  private logPath = '';
  private initialized = false;

  private ensureInit(): void {
    if (this.initialized) return;
    try {
      const dir  = app.getPath('userData');
      this.logPath = path.join(dir, 'voice-typer.log');
      // Alte Logs kürzen (max ~100 KB behalten)
      if (fs.existsSync(this.logPath)) {
        const stat = fs.statSync(this.logPath);
        if (stat.size > 200_000) {
          const content = fs.readFileSync(this.logPath, 'utf8');
          fs.writeFileSync(this.logPath, content.slice(-100_000), 'utf8');
        }
      }
    } catch { /* Vor app.ready */ }
    this.initialized = true;
  }

  private write(level: Level, msg: string, err?: unknown): void {
    this.ensureInit();
    const ts   = new Date().toISOString();
    const errStr = err instanceof Error
      ? `\n  ${err.message}${err.stack ? '\n' + err.stack : ''}`
      : err ? `\n  ${String(err)}` : '';
    const line = `[${ts}] [${level}] ${msg}${errStr}\n`;

    process.stdout.write(line);
    if (this.logPath) {
      try { fs.appendFileSync(this.logPath, line); } catch { /* ignore */ }
    }
  }

  debug(msg: string):                          void { this.write('DEBUG', msg); }
  info (msg: string):                          void { this.write('INFO',  msg); }
  warn (msg: string, err?: unknown):           void { this.write('WARN',  msg, err); }
  error(msg: string, err?: unknown):           void { this.write('ERROR', msg, err); }
  getLogPath(): string { return this.logPath; }
}

export const logger = new Logger();
