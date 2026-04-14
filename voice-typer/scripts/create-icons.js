/**
 * Generiert alle App-Icons:
 *  - idle.png / recording.png / processing.png  (Tray-Status, Blitz-Form)
 *  - blitz.png / app.ico  (Taskleiste / Verknüpfung, gelber Blitz)
 */
'use strict';

const zlib = require('zlib');
const fs   = require('fs');
const path = require('path');

// ─── CRC32 ───────────────────────────────────────────────────────────────────
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    t[i] = c;
  }
  return t;
})();
function crc32(buf) {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = CRC_TABLE[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const t   = Buffer.from(type, 'ascii');
  const c   = Buffer.alloc(4); c.writeUInt32BE(crc32(Buffer.concat([t, data])), 0);
  return Buffer.concat([len, t, data, c]);
}

// ─── PNG-Builder ─────────────────────────────────────────────────────────────
const PNG_SIG = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

function buildPng(size, pixelsFn) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  let pos = 0;
  for (let y = 0; y < size; y++) {
    raw[pos++] = 0;
    for (let x = 0; x < size; x++) {
      const [r, g, b, a] = pixelsFn(x, y);
      raw[pos++] = r; raw[pos++] = g; raw[pos++] = b; raw[pos++] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([
    PNG_SIG,
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// ─── Blitz-Logo ──────────────────────────────────────────────────────────────
// Erzeugt einen Blitz (⚡) der Größe `size`×`size`.
// Form: oberer Arm (oben-rechts → mitte-links), Querbalken, unterer Arm.
function makeBlitzPng(size, fg, bg) {
  const map = new Uint8Array(size * size);  // 0 = bg, 1 = fg

  function fillRow(y, x1, x2) {
    if (y < 0 || y >= size) return;
    for (let x = Math.max(0, x1 | 0); x <= Math.min(size - 1, x2 | 0); x++) {
      map[y * size + x] = 1;
    }
  }

  const s = size / 32;  // Skalierungsfaktor (1.0 bei size=32)

  // ── Oberer Arm (y=2..13): rechte Kante 22→10, Breite 8 ──────────────────
  for (let y = Math.round(2 * s); y <= Math.round(13 * s); y++) {
    const t = (y / s - 2) / 11;                   // Fortschritt 0..1
    const r = Math.round((22 - 12 * t) * s);       // rechte Kante: 22 → 10
    fillRow(y, r - Math.round(8 * s), r);
  }

  // ── Querbalken (y=13..17): x=3..24 ───────────────────────────────────────
  for (let y = Math.round(13 * s); y <= Math.round(17 * s); y++) {
    fillRow(y, Math.round(3 * s), Math.round(24 * s));
  }

  // ── Unterer Arm (y=17..29): linke Kante 14→2, Breite 8 ──────────────────
  for (let y = Math.round(17 * s); y <= Math.round(29 * s); y++) {
    const t = (y / s - 17) / 12;                  // Fortschritt 0..1
    const l = Math.round((14 - 12 * t) * s);       // linke Kante: 14 → 2
    fillRow(y, l, l + Math.round(8 * s));
  }

  return buildPng(size, (x, y) => map[y * size + x] ? fg : bg);
}

// ─── ICO-Builder (PNG-in-ICO, Windows Vista+) ────────────────────────────────
function makeIco(pngBuf32) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);   // reserved
  header.writeUInt16LE(1, 2);   // type ICO
  header.writeUInt16LE(1, 4);   // 1 Bild

  const dir = Buffer.alloc(16);
  dir[0] = 32; dir[1] = 32;    // 32×32
  dir.writeUInt16LE(1,  4);    // planes
  dir.writeUInt16LE(32, 6);    // bpp
  dir.writeUInt32LE(pngBuf32.length, 8);
  dir.writeUInt32LE(6 + 16,           12); // Offset = Header + Dir

  return Buffer.concat([header, dir, pngBuf32]);
}

// ─── Dateien schreiben ───────────────────────────────────────────────────────
const OUT = path.join(__dirname, '..', 'assets', 'icons');
fs.mkdirSync(OUT, { recursive: true });

// Tray-Icons: farbige Blitze auf transparentem Hintergrund
const status = [
  { name: 'idle.png',       fg: [60,  200, 60,  255], bg: [0,0,0,0] },
  { name: 'recording.png',  fg: [220, 40,  40,  255], bg: [0,0,0,0] },
  { name: 'processing.png', fg: [220, 160, 0,   255], bg: [0,0,0,0] },
];
for (const { name, fg, bg } of status) {
  fs.writeFileSync(path.join(OUT, name), makeBlitzPng(32, fg, bg));
  console.log(`  ✓ ${name}`);
}

// App-Icon: gelber Blitz auf dunklem Hintergrund
const YELLOW = [249, 226, 175, 255];  // #f9e2af
const DARK   = [30,  30,  46,  255];  // #1e1e2e
const blitzPng = makeBlitzPng(32, YELLOW, DARK);
fs.writeFileSync(path.join(OUT, 'blitz.png'), blitzPng);
console.log('  ✓ blitz.png');

fs.writeFileSync(path.join(OUT, 'app.ico'), makeIco(blitzPng));
console.log('  ✓ app.ico  (Blitz-Logo)');

console.log('Icons erstellt.');
