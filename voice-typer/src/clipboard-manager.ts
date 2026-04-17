import { clipboard }   from 'electron';
import { execFile }    from 'child_process';
import * as fs   from 'fs';
import * as path from 'path';
import * as os   from 'os';
import { promisify }   from 'util';
import { logger } from './logger';

const execFileAsync = promisify(execFile);

// VBScript: Ctrl+V simulieren – kein PowerShell, kein .NET-Overhead.
// Die Klammerschreibweise "^(v)" stellt sicher, dass der Ctrl-Modifier nur
// für 'v' aktiv ist und sauber losgelassen wird. Ohne Klammern bleibt Ctrl
// in WSH/SendKeys gelegentlich "hängen" und die nächste Taste im aktiven
// Fenster wird Ctrl-modifiziert (z.B. Ctrl+W → schließt Fenster/Tab).
const VBS_SEND_CTRL_V = 'CreateObject("WScript.Shell").SendKeys "^(v)"';

export class ClipboardManager {
  /** Setzt den Text in die Zwischenablage. */
  setText(text: string): void {
    clipboard.writeText(text);
    logger.info(`Zwischenablage gesetzt (${text.length} Zeichen).`);
  }

  /** Sendet Ctrl+V an das aktuell fokussierte Fenster (Windows). */
  async simulatePaste(): Promise<void> {
    if (process.platform !== 'win32') {
      logger.warn('simulatePaste nur auf Windows implementiert.');
      return;
    }
    const scriptPath = path.join(os.tmpdir(), 'vt-paste.vbs');
    try {
      fs.writeFileSync(scriptPath, VBS_SEND_CTRL_V, 'utf8');
      await execFileAsync('wscript', [
        '//nologo',
        scriptPath,
      ], { timeout: 3_000, windowsHide: true });
      logger.info('Ctrl+V gesendet.');
    } catch (err) {
      logger.warn('Ctrl+V simulieren fehlgeschlagen.', err);
    } finally {
      try { fs.unlinkSync(scriptPath); } catch { /* ignore */ }
    }
  }
}

