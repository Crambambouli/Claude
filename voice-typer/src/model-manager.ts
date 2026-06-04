import * as fs    from 'fs';
import * as path  from 'path';
import * as https from 'https';
import * as http  from 'http';
import * as os    from 'os';
import { logger } from './logger';

const HF_BASE      = 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/';
const MAX_REDIRECTS = 10;

// Bekannte Modell-Kennungen → Dateinamen
const MODEL_MAP: Record<string, string> = {
  'tiny':            'ggml-tiny.bin',
  'base':            'ggml-base.bin',
  'small':           'ggml-small.bin',
  'medium':          'ggml-medium.bin',
  'large-v1':        'ggml-large-v1.bin',
  'large-v2':        'ggml-large-v2.bin',
  'large-v3':        'ggml-large-v3.bin',
  'large-v3-q5_0':   'ggml-large-v3-q5_0.bin',
  'large-v3-turbo':  'ggml-large-v3-turbo.bin',
};

export class ModelManager {
  static filename(model: string): string {
    return MODEL_MAP[model] ?? `ggml-${model}.bin`;
  }

  static modelPath(model: string): string {
    const appData = process.env['APPDATA'] ?? path.join(os.homedir(), 'AppData', 'Roaming');
    return path.join(appData, 'Blitztext', 'models', ModelManager.filename(model));
  }

  static isDownloaded(model: string): boolean {
    const p = ModelManager.modelPath(model);
    return fs.existsSync(p) && fs.statSync(p).size > 0;
  }

  static async ensureModel(model: string, onProgress?: (pct: number) => void): Promise<void> {
    if (ModelManager.isDownloaded(model)) return;

    const dest = ModelManager.modelPath(model);
    fs.mkdirSync(path.dirname(dest), { recursive: true });

    const url = HF_BASE + ModelManager.filename(model);
    logger.info(`Lade Whisper-Modell "${model}" herunter: ${url}`);
    await downloadWithRedirects(url, dest, MAX_REDIRECTS, onProgress);
    logger.info(`Modell gespeichert: ${dest}`);
  }
}

function downloadWithRedirects(
  url: string,
  dest: string,
  maxRedirects: number,
  onProgress?: (pct: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    if (maxRedirects < 0) {
      reject(new Error('Zu viele HTTP-Weiterleitungen beim Modell-Download.'));
      return;
    }

    const lib = url.startsWith('https') ? https : http;
    lib.get(url, (res) => {
      if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        resolve(downloadWithRedirects(res.headers.location, dest, maxRedirects - 1, onProgress));
        return;
      }

      if (res.statusCode !== 200) {
        reject(new Error(`HTTP ${res.statusCode} beim Download von ${url}`));
        return;
      }

      const total = parseInt(res.headers['content-length'] ?? '0', 10);
      let downloaded = 0;

      const tmp = dest + '.part';
      const out = fs.createWriteStream(tmp);

      res.on('data', (chunk: Buffer) => {
        downloaded += chunk.length;
        if (total > 0 && onProgress) {
          onProgress(Math.round((downloaded / total) * 100));
        }
      });

      res.pipe(out);

      out.on('finish', () => {
        out.close(() => {
          try {
            fs.renameSync(tmp, dest);
            resolve();
          } catch (err) {
            reject(err);
          }
        });
      });

      out.on('error', (err) => {
        fs.unlink(tmp, () => {});
        reject(err);
      });

      res.on('error', (err) => {
        fs.unlink(tmp, () => {});
        reject(err);
      });
    }).on('error', reject);
  });
}
