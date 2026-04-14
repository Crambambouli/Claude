import { execFile } from 'child_process';
import * as fs   from 'fs';
import * as path from 'path';
import * as os   from 'os';
import { promisify } from 'util';
import { WindowInfo } from './types';
import { logger } from './logger';

const execFileAsync = promisify(execFile);

// ─── PowerShell-Skript ───────────────────────────────────────────────────────
// Gibt zurück: HWND|ProcessName|Title
const PS_GET_FOREGROUND = `
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public class VTWinAPI {
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet=CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int count);
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
}
'@ -ErrorAction SilentlyContinue
$hwnd = [VTWinAPI]::GetForegroundWindow()
$sb = New-Object System.Text.StringBuilder 256
[VTWinAPI]::GetWindowText($hwnd, $sb, 256) | Out-Null
$pid = [uint32]0
[VTWinAPI]::GetWindowThreadProcessId($hwnd, [ref]$pid) | Out-Null
$proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
$procName = if ($proc) { $proc.ProcessName } else { 'unknown' }
Write-Output ("$($hwnd.ToInt64())|$procName|$($sb.ToString())")
`.trim();

// PS-Skript für SetForegroundWindow per HWND
const PS_SET_FOREGROUND = (hwnd: string) => `
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class VTSetFG {
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool ShowWindow(IntPtr hwnd, int cmd);
}
'@ -ErrorAction SilentlyContinue
$hwnd = [IntPtr]${hwnd}
[VTSetFG]::ShowWindow($hwnd, 9) | Out-Null
[VTSetFG]::SetForegroundWindow($hwnd) | Out-Null
`.trim();

// ─── Skript-Cache ─────────────────────────────────────────────────────────────
let getFgScriptPath = '';
function ensureGetFgScript(): string {
  if (!getFgScriptPath || !fs.existsSync(getFgScriptPath)) {
    getFgScriptPath = path.join(os.tmpdir(), 'vt-get-fg.ps1');
    fs.writeFileSync(getFgScriptPath, PS_GET_FOREGROUND, 'utf8');
  }
  return getFgScriptPath;
}

// ─── Öffentliche API ──────────────────────────────────────────────────────────

/** Gibt Informationen über das aktuell fokussierte Fenster zurück. */
export async function getActiveWindowInfo(): Promise<WindowInfo | null> {
  if (process.platform !== 'win32') {
    logger.debug('Window-Detection nur auf Windows unterstützt.');
    return null;
  }
  try {
    const scriptPath = ensureGetFgScript();
    const { stdout } = await execFileAsync('powershell', [
      '-NonInteractive',
      '-NoProfile',
      '-ExecutionPolicy', 'Bypass',
      '-File', scriptPath,
    ], { timeout: 5_000 });

    const line = stdout.trim();
    const idx1 = line.indexOf('|');
    const idx2 = line.indexOf('|', idx1 + 1);
    if (idx1 === -1 || idx2 === -1) return null;

    const hwnd        = line.slice(0, idx1).trim();
    const processName = line.slice(idx1 + 1, idx2).trim();
    const title       = line.slice(idx2 + 1).trim();

    return { hwnd, processName, title };
  } catch (err) {
    logger.warn('getActiveWindowInfo fehlgeschlagen.', err);
    return null;
  }
}

/** Bringt ein Fenster anhand seines HWND in den Vordergrund. */
export async function focusWindowByHwnd(hwnd: string): Promise<void> {
  if (process.platform !== 'win32') return;
  const script = PS_SET_FOREGROUND(hwnd);
  const tmpPath = path.join(os.tmpdir(), 'vt-set-fg.ps1');
  try {
    fs.writeFileSync(tmpPath, script, 'utf8');
    await execFileAsync('powershell', [
      '-NonInteractive',
      '-NoProfile',
      '-ExecutionPolicy', 'Bypass',
      '-File', tmpPath,
    ], { timeout: 3_000 });
  } catch (err) {
    logger.warn(`focusWindowByHwnd(${hwnd}) fehlgeschlagen.`, err);
  } finally {
    try { fs.unlinkSync(tmpPath); } catch { /* ignore */ }
  }
}
