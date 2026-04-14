/**
 * Generiert die PNG-Tray-Icons für alle App-Zustände.
 * Verwendet nur Node.js Built-ins (zlib) – keine externen Abhängigkeiten.
 */
'use strict';

const zlib = require('zlib');
const fs   = require('fs');
const path = require('path');

// ─── CRC32 Tabelle ───────────────────────────────────────────────────────────
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

function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const t   = Buffer.from(type, 'ascii');
  const crcInput = Buffer.concat([t, data]);
  const c   = Buffer.alloc(4); c.writeUInt32BE(crc32(crcInput), 0);
  return Buffer.concat([len, t, data, c]);
}

/**
 * Erstellt ein quadratisches RGBA-PNG mit Kreis.
 * @param {number} size      Kantenlänge in Pixel
 * @param {number[]} fg      [R,G,B,A] Kreisfarbe
 * @param {number[]} bg      [R,G,B,A] Hintergrundfarbe
 * @param {number} [radius]  Kreisradius (default: size*0.42)
 */
function makeCirclePng(size, fg, bg, radius) {
  radius = radius ?? size * 0.42;
  const cx = size / 2;
  const cy = size / 2;

  const PNG_SIG = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

  // IHDR: width, height, bit-depth=8, colortype=6 (RGBA)
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6;

  // Raw image data: filter byte 0 + RGBA per row
  const raw = Buffer.alloc(size * (size * 4 + 1));
  let pos = 0;
  for (let y = 0; y < size; y++) {
    raw[pos++] = 0; // filter type: None
    for (let x = 0; x < size; x++) {
      const dx = x - cx, dy = y - cy;
      const inside = Math.sqrt(dx * dx + dy * dy) <= radius;
      const [r, g, b, a] = inside ? fg : bg;
      raw[pos++] = r; raw[pos++] = g; raw[pos++] = b; raw[pos++] = a;
    }
  }

  const compressed = zlib.deflateSync(raw, { level: 9 });

  return Buffer.concat([
    PNG_SIG,
    chunk('IHDR', ihdr),
    chunk('IDAT', compressed),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ─── Icon-Definitionen ───────────────────────────────────────────────────────
const ICONS = [
  { name: 'idle.png',       fg: [60, 200, 60, 255],   bg: [0, 0, 0, 0] },
  { name: 'recording.png',  fg: [220, 40,  40, 255],  bg: [0, 0, 0, 0] },
  { name: 'processing.png', fg: [220, 160, 0,  255],  bg: [0, 0, 0, 0] },
];

const OUT_DIR = path.join(__dirname, '..', 'assets', 'icons');
fs.mkdirSync(OUT_DIR, { recursive: true });

for (const { name, fg, bg } of ICONS) {
  const buf = makeCirclePng(32, fg, bg);
  fs.writeFileSync(path.join(OUT_DIR, name), buf);
  console.log(`  ✓ assets/icons/${name}`);
}

// Einfaches app.ico: 32×32 grüner Kreis (kopiere idle.png als ico)
// Für Produktion: echtes .ico mit mehreren Größen verwenden.
fs.copyFileSync(
  path.join(OUT_DIR, 'idle.png'),
  path.join(OUT_DIR, 'app.ico'),
);
console.log('  ✓ assets/icons/app.ico');
console.log('Icons created.');
