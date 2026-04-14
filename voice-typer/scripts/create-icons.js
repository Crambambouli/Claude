/**
 * Generiert alle App-Icons:
 *  - idle.png / recording.png / processing.png  (Tray, farbige Blitze)
 *  - app.ico  (Installer / Taskleiste, goldgelber Blitz, 16+32 px)
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
// Scanlines im 32×32-Designraster [y, x_links, x_rechts].
// Skaliert auf beliebige Größe (16, 32, 48 …).
//
//  Aufbau:
//    Oberer Arm   : diagonal rechts-oben → links-mitte   (7 px breit)
//    Querbalken   : breite horizontale Verbindung (Knick) (22 px breit)
//    Unterer Arm  : diagonal rechts-mitte → links-unten  (7 px breit)
//
const BOLT_LINES = [
  // oberer Arm (rechts oben → links mitte)
  [ 2, 14, 20], [ 3, 13, 19], [ 4, 12, 18], [ 5, 11, 17],
  [ 6, 10, 16], [ 7,  9, 15], [ 8,  8, 14], [ 9,  7, 13],
  [10,  6, 12], [11,  5, 11], [12,  4, 10],
  // Querbalken (Knick – der charakteristische Zickzack)
  [13,  3, 24], [14,  3, 24],
  // unterer Arm (rechts von Querbalken → links unten)
  [15, 16, 22], [16, 15, 21], [17, 14, 20], [18, 13, 19],
  [19, 12, 18], [20, 11, 17], [21, 10, 16], [22,  9, 15],
  [23,  8, 14], [24,  7, 13], [25,  6, 12], [26,  5, 11],
  [27,  4, 10],
];

function makeBlitzPng(size, fg, bg) {
  const s  = size / 32;
  const px = new Uint8Array(size * size);

  for (const [y32, x1_32, x2_32] of BOLT_LINES) {
    const row = Math.round(y32 * s);
    if (row < 0 || row >= size) continue;
    const c1 = Math.round(x1_32 * s);
    const c2 = Math.round(x2_32 * s);
    for (let x = Math.max(0, c1); x <= Math.min(size - 1, c2); x++)
      px[row * size + x] = 1;
  }

  return buildPng(size, (x, y) => px[y * size + x] ? fg : bg);
}

// ─── ICO-Builder – 2 Größen (16×16 + 32×32) ─────────────────────────────────
function makeIco(png16, png32) {
  const COUNT  = 2;
  const DIRLEN = 16;
  const offset16 = 6 + COUNT * DIRLEN;
  const offset32 = offset16 + png16.length;

  function dir(w, h, pngBuf, offset) {
    const d = Buffer.alloc(DIRLEN);
    d[0] = w; d[1] = h;
    d.writeUInt16LE(1,  4);  // planes
    d.writeUInt16LE(32, 6);  // bpp
    d.writeUInt32LE(pngBuf.length, 8);
    d.writeUInt32LE(offset,       12);
    return d;
  }

  const header = Buffer.alloc(6);
  header.writeUInt16LE(0,     0);
  header.writeUInt16LE(1,     2);  // ICO
  header.writeUInt16LE(COUNT, 4);

  return Buffer.concat([header, dir(16, 16, png16, offset16),
                                dir(32, 32, png32, offset32),
                                png16, png32]);
}

// ─── Dateien schreiben ───────────────────────────────────────────────────────
const OUT = path.join(__dirname, '..', 'assets', 'icons');
fs.mkdirSync(OUT, { recursive: true });

const DARK   = [30,  30,  46,  255];  // #1e1e2e
const YELLOW = [249, 226, 175, 255];  // #f9e2af

// Tray-Icons: farbige Blitze auf transparentem Hintergrund
const tray = [
  { name: 'idle.png',       fg: [60,  200,  60, 255] },
  { name: 'recording.png',  fg: [220,  40,  40, 255] },
  { name: 'processing.png', fg: [220, 160,   0, 255] },
];
for (const { name, fg } of tray) {
  fs.writeFileSync(path.join(OUT, name), makeBlitzPng(32, fg, [0, 0, 0, 0]));
  console.log(`  ✓ ${name}`);
}

// App-Icon: goldgelber Blitz, 16 px + 32 px im ICO
const blitz16 = makeBlitzPng(16, YELLOW, DARK);
const blitz32 = makeBlitzPng(32, YELLOW, DARK);
fs.writeFileSync(path.join(OUT, 'blitz.png'), blitz32);
console.log('  ✓ blitz.png  (32×32)');

fs.writeFileSync(path.join(OUT, 'app.ico'), makeIco(blitz16, blitz32));
console.log('  ✓ app.ico    (16×16 + 32×32, Blitz-Logo)');

console.log('Icons erstellt.');
