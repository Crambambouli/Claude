import { clipboard }   from 'electron';
import { execFile }    from 'child_process';
import * as fs   from 'fs';
import * as path from 'path';
import * as os   from 'os';
import { promisify }   from 'util';
import { focusWindowByHwnd } from './window-detection';
import { logger } from './logger';

const execFileAsync = promisify(execFile);

// VBScript: Ctrl+V simulieren – kein PowerShell, kein .NET-Overhead
const VBS_SEND_CTRL_V = 'CreateObject("WScript.Shell").SendKeys "^v"';

export class ClipboardManager {
  /** Setzt den Text in die Zwischenablage. */
  setText(text: string): void {
    clipboard.writeText(text);
    logger.info(`Zwischenablage gesetzt (${text.length} Zeichen).`);
  }

  /** Fokussiert das Ziel-Fenster und fügt per Ctrl+V ein. */
  async focusAndPaste(targetHwnd: string): Promise<void> {
    logger.info(`Fokussiere Fenster HWND ${targetHwnd} …`);
    await focusWindowByHwnd(targetHwnd);
    // Kurze Pause, damit das Fenster tatsächlich fokussiert ist
    await sleep(150);
    await this.simulatePaste();
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

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
