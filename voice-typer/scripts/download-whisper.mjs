#!/usr/bin/env node
// download-whisper.mjs – lädt whisper-server.exe (whisper.cpp) für Windows x64 herunter
// Nutzung: node scripts/download-whisper.mjs

import https from 'https';
import fs    from 'fs';
import path  from 'path';
import zlib  from 'zlib';
import { pipeline } from 'stream/promises';
import { Readable } from 'stream';

const REPO    = 'ggerganov/whisper.cpp';
const API_URL = `https://api.github.com/repos/${REPO}/releases/latest`;
const BIN_DIR = path.resolve(import.meta.dirname ?? path.dirname(new URL(import.meta.url).pathname), '..', 'bin');
const DEST    = path.join(BIN_DIR, 'whisper-server.exe');

// ─── HTTP-Helpers ─────────────────────────────────────────────────────────────

function get(url, options = {}) {
  return new Promise((resolve, reject) => {
    const opts = {
      headers: { 'User-Agent': 'blitztext-setup/1.0' },
      ...options,
    };
    https.get(url, opts, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        resolve(get(res.headers.location, options));
        return;
      }
      resolve(res);
    }).on('error', reject);
  });
}

async function getJson(url) {
  const res = await get(url);
  return new Promise((resolve, reject) => {
    let d = '';
    res.on('data', c => d += c);
    res.on('end', () => {
      try { resolve(JSON.parse(d)); }
      catch (e) { reject(new Error(`JSON-Parse-Fehler: ${e.message}\n${d.slice(0, 200)}`)); }
    });
    res.on('error', reject);
  });
}

async function downloadFile(url, dest) {
  console.log(`  URL: ${url}`);

  let res = await get(url);

  // Folge bis zu 10 Weiterleitungen (get() folgt bereits, aber explizit für Klarheit)
  let redirects = 0;
  while (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location && redirects < 10) {
    res = await get(res.headers.location);
    redirects++;
  }

  if (res.statusCode !== 200) {
    throw new Error(`HTTP ${res.statusCode} beim Download von ${url}`);
  }

  const total = parseInt(res.headers['content-length'] ?? '0', 10);
  let downloaded = 0;
  let lastPct = -1;

  const tmp = dest + '.part';
  const out = fs.createWriteStream(tmp);

  res.on('data', (chunk) => {
    downloaded += chunk.length;
    if (total > 0) {
      const pct = Math.floor((downloaded / total) * 100);
      if (pct !== lastPct && pct % 10 === 0) {
        process.stdout.write(`\r  ${pct}% (${(downloaded / 1_048_576).toFixed(1)} MB / ${(total / 1_048_576).toFixed(1)} MB)`);
        lastPct = pct;
      }
    }
  });

  await pipeline(res, out);
  process.stdout.write('\r  100%                                    \n');

  fs.renameSync(tmp, dest);
}

// ─── ZIP-Extraktion ───────────────────────────────────────────────────────────

async function extractExeFromZip(zipPath, exeName, outputPath) {
  // Minimaler ZIP-Parser: suche Local-File-Header für exeName
  const data = fs.readFileSync(zipPath);

  // End-of-central-directory (EOCD) Signatur: 0x06054b50
  let eocdOffset = -1;
  for (let i = data.length - 22; i >= 0; i--) {
    if (data.readUInt32LE(i) === 0x06054b50) { eocdOffset = i; break; }
  }
  if (eocdOffset === -1) throw new Error('Kein ZIP-EOCD gefunden.');

  const cdOffset = data.readUInt32LE(eocdOffset + 16);
  const cdEntries = data.readUInt16LE(eocdOffset + 8);

  let cdPos = cdOffset;
  for (let i = 0; i < cdEntries; i++) {
    if (data.readUInt32LE(cdPos) !== 0x02014b50) throw new Error('Ungültiger Central-Directory-Eintrag.');

    const compMethod    = data.readUInt16LE(cdPos + 10);
    const compSize      = data.readUInt32LE(cdPos + 20);
    const uncompSize    = data.readUInt32LE(cdPos + 24);
    const fnLen         = data.readUInt16LE(cdPos + 28);
    const extraLen      = data.readUInt16LE(cdPos + 30);
    const commentLen    = data.readUInt16LE(cdPos + 32);
    const localOffset   = data.readUInt32LE(cdPos + 42);
    const filename      = data.slice(cdPos + 46, cdPos + 46 + fnLen).toString('utf8');

    if (path.basename(filename).toLowerCase() === exeName.toLowerCase()) {
      // Local-File-Header lesen
      const lfhExtraLen = data.readUInt16LE(localOffset + 28);
      const dataStart   = localOffset + 30 + fnLen + lfhExtraLen;
      const compressed  = data.slice(dataStart, dataStart + compSize);

      if (compMethod === 0) {
        // Stored (keine Komprimierung)
        fs.writeFileSync(outputPath, compressed.slice(0, uncompSize));
      } else if (compMethod === 8) {
        // Deflate
        const inflated = zlib.inflateRawSync(compressed);
        fs.writeFileSync(outputPath, inflated);
      } else {
        throw new Error(`Nicht unterstützte ZIP-Komprimierung: ${compMethod}`);
      }

      console.log(`  Extrahiert: ${filename} → ${outputPath}`);
      return;
    }

    cdPos += 46 + fnLen + extraLen + commentLen;
  }

  throw new Error(`${exeName} nicht in ZIP gefunden. Verfügbare Dateien:\n${listZipEntries(data, cdOffset, cdEntries)}`);
}

function listZipEntries(data, cdOffset, cdEntries) {
  const names = [];
  let pos = cdOffset;
  for (let i = 0; i < cdEntries; i++) {
    if (data.readUInt32LE(pos) !== 0x02014b50) break;
    const fnLen     = data.readUInt16LE(pos + 28);
    const extraLen  = data.readUInt16LE(pos + 30);
    const commentLen = data.readUInt16LE(pos + 32);
    names.push(data.slice(pos + 46, pos + 46 + fnLen).toString('utf8'));
    pos += 46 + fnLen + extraLen + commentLen;
  }
  return names.join('\n');
}

// ─── Asset-Auswahl ────────────────────────────────────────────────────────────

function pickAsset(assets) {
  const n = (a) => a.name.toLowerCase();
  const isZip = (a) => a.name.endsWith('.zip');
  const notMac = (a) => !n(a).includes('xcframework') && !n(a).includes('.jar') && !n(a).includes('win32');

  // 1. Vulkan + x64 (GPU ohne CUDA-Toolkit, z.B. neuere Releases)
  const vulkan = assets.find(a => isZip(a) && n(a).includes('vulkan') && n(a).includes('x64'));
  if (vulkan) return vulkan;

  // 2. Nur x64 (kein BLAS, kein CUDA) – komplett standalone, v1.8.x: "whisper-bin-x64.zip"
  const plain = assets.find(a =>
    isZip(a) && notMac(a) && n(a).includes('x64') &&
    !n(a).includes('blas') && !n(a).includes('cublas') && !n(a).includes('cuda'),
  );
  if (plain) return plain;

  // 3. BLAS + x64 (CPU-Beschleunigung)
  const blas = assets.find(a => isZip(a) && notMac(a) && n(a).includes('blas') && n(a).includes('x64') && !n(a).includes('cublas'));
  if (blas) return blas;

  // 4. CUDA + x64 (neuere Version bevorzugen)
  const cuda = assets
    .filter(a => isZip(a) && notMac(a) && (n(a).includes('cublas') || n(a).includes('cuda')) && n(a).includes('x64'))
    .sort((a, b) => b.name.localeCompare(a.name))[0];
  if (cuda) return cuda;

  return null;
}

// ─── Hauptprogramm ────────────────────────────────────────────────────────────

async function main() {
  console.log('=== whisper.cpp Setup ===');

  if (fs.existsSync(DEST)) {
    console.log(`whisper-server.exe bereits vorhanden: ${DEST}`);
    console.log('Setup abgeschlossen (kein Download nötig).');
    return;
  }

  console.log(`Hole neueste Release-Informationen von ${API_URL} …`);
  const release = await getJson(API_URL);
  console.log(`Neueste Version: ${release.tag_name}`);

  const assets = release.assets ?? [];
  if (assets.length === 0) throw new Error('Keine Release-Assets gefunden.');

  const asset = pickAsset(assets);
  if (!asset) {
    const names = assets.map(a => a.name).join('\n  ');
    throw new Error(`Kein passendes Windows-x64-Asset gefunden. Verfügbar:\n  ${names}`);
  }

  console.log(`Ausgewähltes Asset: ${asset.name} (${(asset.size / 1_048_576).toFixed(1)} MB)`);

  fs.mkdirSync(BIN_DIR, { recursive: true });

  const zipPath = path.join(BIN_DIR, asset.name);
  console.log(`Lade herunter → ${zipPath}`);
  await downloadFile(asset.browser_download_url, zipPath);

  // whisper-server.exe suchen; ältere Releases nennen es ggf. anders
  const CANDIDATES = ['whisper-server.exe', 'server.exe', 'main.exe', 'whisper.exe'];
  let extracted = false;
  for (const candidate of CANDIDATES) {
    try {
      console.log(`Extrahiere ${candidate} …`);
      await extractExeFromZip(zipPath, candidate, DEST);
      extracted = true;
      break;
    } catch (e) {
      if (!e.message.includes('nicht in ZIP gefunden')) throw e;
    }
  }
  if (!extracted) {
    // Liste was im ZIP ist und breche ab
    const data = fs.readFileSync(zipPath);
    let eocdOffset = -1;
    for (let i = data.length - 22; i >= 0; i--) {
      if (data.readUInt32LE(i) === 0x06054b50) { eocdOffset = i; break; }
    }
    const cdOffset = data.readUInt32LE(eocdOffset + 16);
    const cdEntries = data.readUInt16LE(eocdOffset + 8);
    fs.unlinkSync(zipPath);
    throw new Error(`Kein Server-Binary im ZIP gefunden. Inhalt:\n${listZipEntries(data, cdOffset, cdEntries)}`);
  }

  fs.unlinkSync(zipPath);
  console.log(`whisper-server.exe bereit: ${DEST}`);
  console.log('=== Setup abgeschlossen ===');
}

main().catch((err) => {
  console.error('Fehler beim Setup:', err.message);
  process.exit(1);
});
