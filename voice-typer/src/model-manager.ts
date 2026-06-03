import * as fs    from 'fs';
import * as path  from 'path';
import * as https from 'https';
import * as http  from 'http';
import * as os    from 'os';
import { logger } from './logger';

const MODEL_FILENAME = 'ggml-large-v3-q5_0.bin';
const MODEL_URL      = `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${MODEL_FILENAME}`;
const MAX_REDIRECTS  = 10;

export class ModelManager {
  static modelPath(): string {
    const appData = process.env['APPDATA'] ?? path.join(os.homedir(), 'AppData', 'Roaming');
    return path.join(appData, 'Blitztext', 'models', MODEL_FILENAME);
  }

  static isDownloaded(): boolean {
    const p = ModelManager.modelPath();
    return fs.existsSync(p) && fs.statSync(p).size > 0;
  }

  static async ensureModel(onProgress?: (pct: number) => void): Promise<void> {
    if (ModelManager.isDownloaded()) return;

    const dest = ModelManager.modelPath();
    fs.mkdirSync(path.dirname(dest), { recursive: true });

    logger.info(`Lade Whisper-Modell herunter: ${MODEL_URL}`);
    await downloadWithRedirects(MODEL_URL, dest, MAX_REDIRECTS, onProgress);
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
