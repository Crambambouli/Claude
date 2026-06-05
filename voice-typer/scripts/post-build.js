/**
 * Kopiert die Renderer-HTML-Dateien nach dist/renderer/
 * und die i18n-JSON-Dateien nach dist/i18n/.
 * Wird nach "tsc" als postbuild-Hook ausgeführt.
 */
'use strict';

const fs   = require('fs');
const path = require('path');

// ─── Renderer-Dateien ────────────────────────────────────────────────────────
const RENDERER_SRC  = path.join(__dirname, '..', 'src',  'renderer');
const RENDERER_DEST = path.join(__dirname, '..', 'dist', 'renderer');

fs.mkdirSync(RENDERER_DEST, { recursive: true });

for (const file of fs.readdirSync(RENDERER_SRC)) {
  if (file.endsWith('.html') || file.endsWith('.css') || file.endsWith('.js')) {
    fs.copyFileSync(path.join(RENDERER_SRC, file), path.join(RENDERER_DEST, file));
    console.log(`  ✓ dist/renderer/${file}`);
  }
}
console.log('Renderer-Dateien kopiert.');

// ─── i18n-Dateien ────────────────────────────────────────────────────────────
const I18N_SRC  = path.join(__dirname, '..', 'src',  'i18n');
const I18N_DEST = path.join(__dirname, '..', 'dist', 'i18n');

fs.mkdirSync(I18N_DEST, { recursive: true });

for (const file of fs.readdirSync(I18N_SRC)) {
  if (file.endsWith('.json')) {
    fs.copyFileSync(path.join(I18N_SRC, file), path.join(I18N_DEST, file));
    console.log(`  ✓ dist/i18n/${file}`);
  }
}
console.log('i18n-Dateien kopiert.');
