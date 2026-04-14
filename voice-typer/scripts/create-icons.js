/**
 * Generiert alle App-Icons:
 *  - idle.png / recording.png / processing.png  (Tray-Status)
 *  - app.ico  (Taskleiste / Verknüpfung)  mit "VT"-Logo
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

// ─── Tray-Status-Icons (farbige Kreise) ─────────────────────────────────────
function makeCirclePng(size, fg, bg) {
  const cx = size / 2, cy = size / 2, r = size * 0.42;
  return buildPng(size, (x, y) => {
    const inside = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2) <= r;
    return inside ? fg : bg;
  });
}

// ─── VT-Logo (Pixel-Font, 5×7 pro Buchstabe, Skalierung 3) ──────────────────
const FONT_5x7 = {
  V: [
    [1,0,0,0,1],
    [1,0,0,0,1],
    [1,0,0,0,1],
    [0,1,0,1,0],
    [0,1,0,1,0],
    [0,0,1,0,0],
    [0,0,1,0,0],
  ],
  T: [
    [1,1,1,1,1],
    [0,0,1,0,0],
    [0,0,1,0,0],
    [0,0,1,0,0],
    [0,0,1,0,0],
    [0,0,1,0,0],
    [0,0,1,0,0],
  ],
};

function makeVtPng(size = 32) {
  const SCALE  = 3;
  const BG     = [30, 30, 46, 255];   // #1e1e2e
  const FG     = [137, 180, 250, 255]; // #89b4fa
  const LW     = 5 * SCALE;            // 15 px
  const LH     = 7 * SCALE;            // 21 px
  const GAP    = 2;
  const startX = Math.floor((size - LW * 2 - GAP) / 2); // horizontal zentrieren
  const startY = Math.floor((size - LH) / 2);

  // Pixel-Map aufbauen
  const map = Array.from({ length: size }, () =>
    Array.from({ length: size }, () => [...BG])
  );

  ['V', 'T'].forEach((letter, li) => {
    const ox = startX + li * (LW + GAP);
    FONT_5x7[letter].forEach((row, r) => {
      row.forEach((on, c) => {
        if (!on) return;
        for (let dy = 0; dy < SCALE; dy++)
          for (let dx = 0; dx < SCALE; dx++) {
            const px = ox + c * SCALE + dx;
            const py = startY + r * SCALE + dy;
            if (px < size && py < size) map[py][px] = [...FG];
          }
      });
    });
  });

  return buildPng(size, (x, y) => map[y][x]);
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
  dir.writeUInt32LE(pngBuf32.length, 8);   // Datengröße
  dir.writeUInt32LE(6 + 16,           12); // Offset = Header + Dir

  return Buffer.concat([header, dir, pngBuf32]);
}

// ─── Dateien schreiben ───────────────────────────────────────────────────────
const OUT = path.join(__dirname, '..', 'assets', 'icons');
fs.mkdirSync(OUT, { recursive: true });

const status = [
  { name: 'idle.png',       fg: [60,  200, 60,  255], bg: [0,0,0,0] },
  { name: 'recording.png',  fg: [220, 40,  40,  255], bg: [0,0,0,0] },
  { name: 'processing.png', fg: [220, 160, 0,   255], bg: [0,0,0,0] },
];
for (const { name, fg, bg } of status) {
  fs.writeFileSync(path.join(OUT, name), makeCirclePng(32, fg, bg));
  console.log(`  ✓ ${name}`);
}

const vtPng = makeVtPng(32);
fs.writeFileSync(path.join(OUT, 'vt-logo.png'), vtPng);
console.log('  ✓ vt-logo.png');

fs.writeFileSync(path.join(OUT, 'app.ico'), makeIco(vtPng));
console.log('  ✓ app.ico  (VT-Logo)');

console.log('Icons erstellt.');
