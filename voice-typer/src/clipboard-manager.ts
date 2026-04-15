import { clipboard }   from 'electron';
import { execFile }    from 'child_process';
import * as fs   from 'fs';
import * as path from 'path';
import * as os   from 'os';
import { promisify }   from 'util';
import { focusWindowByHwnd } from './window-detection';
import { logger } from './logger';

const execFileAsync = promisify(execFile);

// PowerShell: Ctrl+V simulieren
const PS_SEND_CTRL_V = `
Add-Type -AssemblyName System.Windows.Forms
[System.Windows.Forms.SendKeys]::SendWait('^v')
`.trim();

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
    const scriptPath = path.join(os.tmpdir(), 'vt-paste.ps1');
    try {
      fs.writeFileSync(scriptPath, PS_SEND_CTRL_V, 'utf8');
      await execFileAsync('powershell', [
        '-NonInteractive',
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', scriptPath,
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
