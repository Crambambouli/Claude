/**
 * Kopiert die Renderer-HTML-Dateien nach dist/renderer/.
 * Wird nach "tsc" als postbuild-Hook ausgeführt.
 */
'use strict';

const fs   = require('fs');
const path = require('path');

const SRC  = path.join(__dirname, '..', 'src',  'renderer');
const DEST = path.join(__dirname, '..', 'dist', 'renderer');

fs.mkdirSync(DEST, { recursive: true });

for (const file of fs.readdirSync(SRC)) {
  if (file.endsWith('.html') || file.endsWith('.css') || file.endsWith('.js')) {
    fs.copyFileSync(path.join(SRC, file), path.join(DEST, file));
    console.log(`  ✓ dist/renderer/${file}`);
  }
}
console.log('Renderer-Dateien kopiert.');
