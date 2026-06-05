import { Tray, Menu, nativeImage, app } from 'electron';
import * as path from 'path';
import * as fs   from 'fs';
import { AppState, Mode } from './types';
import { logger } from './logger';
import { t } from './i18n/index';

const MODES: Mode[] = ['Normal', 'Plus', 'Rage', 'Emoji'];

function getStateLabel(state: AppState): string {
  const labels: Record<AppState, string> = {
    idle:       `● ${t('overlay.idle')}`,
    recording:  `● ${t('overlay.recording')}`,
    processing: `● ${t('overlay.processing')}`,
  };
  return labels[state];
}

function getStateTooltip(state: AppState): string {
  const tooltips: Record<AppState, string> = {
    idle:       `${t('app.name')} – ${t('overlay.idle')} (Ctrl+F8)`,
    recording:  `${t('app.name')} – ${t('overlay.recording')} (Ctrl+F8)`,
    processing: `${t('app.name')} – ${t('overlay.processing')}`,
  };
  return tooltips[state];
}

interface TrayOptions {
  mode:            Mode;
  onModeChange:    (m: Mode) => void;
  onSettings:      () => void;
  onToggleOverlay: () => void;
  onExit:          () => void;
}

export class TrayManager {
  private tray: Tray | null = null;
  private state:   AppState = 'idle';
  private mode:    Mode     = 'Normal';
  private options!: TrayOptions;

  init(options: TrayOptions): void {
    this.options = options;
    this.mode    = options.mode;
    this.tray    = new Tray(this.getIcon('idle'));
    this.rebuild();
    logger.info('Tray initialisiert.');
  }

  setState(state: AppState): void {
    this.state = state;
    if (this.tray) {
      this.tray.setImage(this.getIcon(state));
      this.tray.setToolTip(`${t('app.name')} [${this.mode}] – ${getStateTooltip(state)}`);
    }
    // Kein rebuild() – Win32-HMENU-Neuaufbau löst Fokus-Interaktionen aus
  }

  setMode(mode: Mode): void {
    this.mode = mode;
    this.rebuild();
  }

  private rebuild(): void {
    if (!this.tray) return;

    const menu = Menu.buildFromTemplate([
      {
        label:   getStateLabel(this.state),
        enabled: false,
        icon:    this.getSmallIcon(this.state),
      },
      { type: 'separator' },
      {
        label:   'Modus',
        submenu: MODES.map(m => ({
          label:   t(`mode.${m}`),
          type:    'radio' as const,
          checked: m === this.mode,
          click:   () => {
            this.mode = m;
            this.options.onModeChange(m);
            this.rebuild();
          },
        })),
      },
      { type: 'separator' },
      {
        label: `▣  ${t('tray.toggleOverlay')}`,
        click: () => this.options.onToggleOverlay(),
      },
      {
        label: `⚙  ${t('tray.settings')}`,
        click: () => this.options.onSettings(),
      },
      {
        label: `✕  ${t('tray.quit')}`,
        click: () => this.options.onExit(),
      },
    ]);

    this.tray.setContextMenu(menu);
    this.tray.setToolTip(
      `${t('app.name')} [${this.mode}] – ${getStateTooltip(this.state)}`
    );
  }

  private getIcon(state: AppState): Electron.NativeImage {
    const iconMap: Record<AppState, string> = {
      idle:       'idle.png',
      recording:  'recording.png',
      processing: 'processing.png',
    };
    return this.loadIcon(iconMap[state]);
  }

  private getSmallIcon(state: AppState): Electron.NativeImage | undefined {
    try {
      const img = this.getIcon(state);
      return img.resize({ width: 16, height: 16 });
    } catch {
      return undefined;
    }
  }

  private loadIcon(filename: string): Electron.NativeImage {
    const candidates = [
      path.join(app.getAppPath(), 'assets', 'icons', filename),
    ];
    for (const p of candidates) {
      if (fs.existsSync(p)) return nativeImage.createFromPath(p);
    }
    logger.warn(`Icon nicht gefunden: ${filename}`);
    return nativeImage.createEmpty();
  }

  destroy(): void {
    this.tray?.destroy();
    this.tray = null;
  }
}
