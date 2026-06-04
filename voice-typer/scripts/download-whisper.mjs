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

function parseZipEntries(data) {
  // End-of-central-directory (EOCD) Signatur: 0x06054b50
  let eocdOffset = -1;
  for (let i = data.length - 22; i >= 0; i--) {
    if (data.readUInt32LE(i) === 0x06054b50) { eocdOffset = i; break; }
  }
  if (eocdOffset === -1) throw new Error('Kein ZIP-EOCD gefunden.');

  const cdOffset  = data.readUInt32LE(eocdOffset + 16);
  const cdEntries = data.readUInt16LE(eocdOffset + 8);

  const entries = [];
  let pos = cdOffset;
  for (let i = 0; i < cdEntries; i++) {
    if (data.readUInt32LE(pos) !== 0x02014b50) throw new Error('Ungültiger Central-Directory-Eintrag.');
    const compMethod  = data.readUInt16LE(pos + 10);
    const compSize    = data.readUInt32LE(pos + 20);
    const uncompSize  = data.readUInt32LE(pos + 24);
    const fnLen       = data.readUInt16LE(pos + 28);
    const extraLen    = data.readUInt16LE(pos + 30);
    const commentLen  = data.readUInt16LE(pos + 32);
    const localOffset = data.readUInt32LE(pos + 42);
    const filename    = data.slice(pos + 46, pos + 46 + fnLen).toString('utf8');
    entries.push({ filename, compMethod, compSize, uncompSize, localOffset, fnLen });
    pos += 46 + fnLen + extraLen + commentLen;
  }
  return entries;
}

function extractEntry(data, entry, destPath) {
  const { localOffset, fnLen, compMethod, compSize, uncompSize } = entry;
  const lfhExtraLen = data.readUInt16LE(localOffset + 28);
  const dataStart   = localOffset + 30 + fnLen + lfhExtraLen;
  const compressed  = data.slice(dataStart, dataStart + compSize);

  if (compMethod === 0) {
    fs.writeFileSync(destPath, compressed.slice(0, uncompSize));
  } else if (compMethod === 8) {
    fs.writeFileSync(destPath, zlib.inflateRawSync(compressed));
  } else {
    throw new Error(`Nicht unterstützte ZIP-Komprimierung: ${compMethod}`);
  }
}

// Extrahiert alle Dateien aus dem ZIP in destDir (flach, ohne Unterverzeichnisse)
function extractAllFromZip(zipPath, destDir) {
  const data    = fs.readFileSync(zipPath);
  const entries = parseZipEntries(data);
  const names   = [];

  for (const entry of entries) {
    const base = path.basename(entry.filename);
    if (!base || base.endsWith('/') || entry.uncompSize === 0) continue; // Verzeichnisse überspringen
    const dest = path.join(destDir, base);
    extractEntry(data, entry, dest);
    console.log(`  Extrahiert: ${entry.filename}`);
    names.push(base);
  }
  return names;
}

// ─── Asset-Auswahl ────────────────────────────────────────────────────────────

function pickAsset(assets) {
  const n = (a) => a.name.toLowerCase();
  const isZip = (a) => a.name.endsWith('.zip');
  const notMac = (a) => !n(a).includes('xcframework') && !n(a).includes('.jar') && !n(a).includes('win32');
  const x64 = (a) => n(a).includes('x64');

  // 1. Vulkan + x64 (GPU, kein CUDA-Toolkit nötig)
  const vulkan = assets.find(a => isZip(a) && notMac(a) && x64(a) && n(a).includes('vulkan'));
  if (vulkan) return vulkan;

  // 2. CUBLAS 12.x + x64 (NVIDIA GPU, CUDA 12 – neueste Version bevorzugen)
  const cuda12 = assets
    .filter(a => isZip(a) && notMac(a) && x64(a) && n(a).includes('cublas') && n(a).includes('12'))
    .sort((a, b) => b.name.localeCompare(a.name))[0];
  if (cuda12) return cuda12;

  // 3. CUBLAS 11.x + x64 (NVIDIA GPU, älterer Treiber)
  const cuda11 = assets.find(a => isZip(a) && notMac(a) && x64(a) && n(a).includes('cublas'));
  if (cuda11) return cuda11;

  // 4. BLAS + x64 (optimierte CPU-Version, kein GPU)
  const blas = assets.find(a => isZip(a) && notMac(a) && x64(a) && n(a).includes('blas') && !n(a).includes('cublas'));
  if (blas) return blas;

  // 5. Plain x64 (CPU, keine Beschleunigung – letzter Ausweg)
  const plain = assets.find(a =>
    isZip(a) && notMac(a) && x64(a) &&
    !n(a).includes('blas') && !n(a).includes('cublas') && !n(a).includes('cuda'),
  );
  if (plain) return plain;

  return null;
}

// ─── Hauptprogramm ────────────────────────────────────────────────────────────

async function main() {
  console.log('=== whisper.cpp Setup ===');

  if (fs.existsSync(DEST)) {
    // Prüfen ob die vorhandene Datei lauffähig ist (Mindestgröße > 100 KB)
    const size = fs.statSync(DEST).size;
    if (size > 100_000) {
      console.log(`whisper-server.exe bereits vorhanden (${(size / 1_048_576).toFixed(1)} MB): ${DEST}`);
      console.log('Setup abgeschlossen (kein Download nötig). Für Neuinstallation: bin\\-Ordner löschen.');
      return;
    }
    console.log(`whisper-server.exe vorhanden aber zu klein (${size} Bytes) – wird neu heruntergeladen.`);
    fs.rmSync(BIN_DIR, { recursive: true, force: true });
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

  // Alle Dateien aus dem ZIP extrahieren (inkl. benötigte DLLs)
  console.log('Extrahiere alle Dateien (EXE + DLLs) …');
  const extracted = extractAllFromZip(zipPath, BIN_DIR);
  fs.unlinkSync(zipPath);

  if (extracted.length === 0) throw new Error('ZIP enthielt keine extrahierbaren Dateien.');
  console.log(`${extracted.length} Datei(en) extrahiert: ${extracted.join(', ')}`);

  // Server-Binary als whisper-server.exe bereitstellen
  const SERVER_CANDIDATES = ['server.exe', 'whisper-server.exe', 'main.exe'];
  let serverBin = '';
  for (const c of SERVER_CANDIDATES) {
    if (extracted.map(n => n.toLowerCase()).includes(c)) { serverBin = c; break; }
  }
  if (!serverBin) throw new Error(`Kein Server-Binary gefunden. Inhalt: ${extracted.join(', ')}`);

  if (serverBin !== 'whisper-server.exe') {
    fs.copyFileSync(path.join(BIN_DIR, serverBin), DEST);
    console.log(`${serverBin} → whisper-server.exe`);
  }

  if (serverBin === 'main.exe') {
    console.warn('WARNUNG: Nur main.exe (CLI-Tool) gefunden – kein HTTP-Server-Binary im ZIP.');
  }

  console.log(`whisper-server.exe bereit: ${DEST}`);
  console.log('=== Setup abgeschlossen ===');
}

main().catch((err) => {
  console.error('Fehler beim Setup:', err.message);
  process.exit(1);
});
