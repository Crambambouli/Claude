#!/usr/bin/env python3
"""
whisper_server.py — HTTP-Server fuer Blitztext (faster-whisper Backend)
Implementiert dieselbe API wie whisper-server.exe:
  GET  /health       -> {"status":"ok"}
  POST /inference    -> multipart/form-data (file=WAV, language=...) -> {"text":"..."}

Aufruf: python whisper_server.py <model_name> <language> <device>
  model_name : faster-whisper-Modellname (z.B. "large-v3", "medium")
  language   : Sprachcode (z.B. "de") oder "auto"
  device     : "cuda" oder "cpu"
"""
import json
import os
import pathlib
import re
import sys
import sysconfig

# CUDA-DLLs aus nvidia-* PyPI-Paketen verfuegbar machen (Windows).
# PATH-Erweiterung + add_dll_directory, VOR allen ctranslate2/faster_whisper-Imports.
# LoadLibrary() (native CTranslate2-C++) sucht in PATH, nicht in add_dll_directory.
if os.name == 'nt':
    _sp      = pathlib.Path(sysconfig.get_paths()['purelib'])
    _nvidia  = _sp / 'nvidia'
    _extra   = []
    if _nvidia.exists():
        for _bin in _nvidia.rglob('bin'):
            if _bin.is_dir():
                _extra.append(str(_bin))
                try:
                    os.add_dll_directory(str(_bin))
                except Exception:
                    pass
    if _extra:
        os.environ['PATH'] = os.pathsep.join(_extra) + os.pathsep + os.environ.get('PATH', '')

import struct
import tempfile
import wave

from http.server import HTTPServer, BaseHTTPRequestHandler
from faster_whisper import WhisperModel

PORT = 8765

MODEL_NAME   = sys.argv[1] if len(sys.argv) > 1 else "large-v3"
LANG_DEFAULT = sys.argv[2] if len(sys.argv) > 2 else "de"
DEVICE       = sys.argv[3] if len(sys.argv) > 3 else "cpu"

# compute_type: int8_float16 ist schneller auf GPU (mischt INT8 + FP16)
COMPUTE_TYPE = "int8_float16" if DEVICE == "cuda" else "int8"

print(f"[fw-server] Lade '{MODEL_NAME}' (device={DEVICE}, compute_type={COMPUTE_TYPE}) ...", flush=True)
model = WhisperModel(MODEL_NAME, device=DEVICE, compute_type=COMPUTE_TYPE)
print("[fw-server] Modell bereit.", flush=True)

# CUDA-Warmup: erzwingt PTX-JIT-Kompilierung bevor erste echte Anfrage kommt.
# Ohne Warmup haengt der erste Inference-Call 60-120s (JIT fuer Blackwell sm_120).
if DEVICE == "cuda":
    print("[fw-server] CUDA-Warmup (PTX-JIT-Kompilierung, bitte warten)...", flush=True)
    _tmp = None
    try:
        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as _f:
            with wave.open(_f, 'wb') as _w:
                _w.setnchannels(1)
                _w.setsampwidth(2)
                _w.setframerate(16000)
                _w.writeframes(b'\x00' * 16000)  # 0.5s Stille
            _tmp = _f.name
        list(model.transcribe(_tmp, language='de', beam_size=1)[0])
        print("[fw-server] CUDA-Warmup abgeschlossen.", flush=True)
    except Exception as _e:
        print(f"[fw-server] CUDA-Warmup-Warnung: {_e}", flush=True)
    finally:
        if _tmp and os.path.exists(_tmp):
            os.unlink(_tmp)


def parse_multipart(content_type: str, body: bytes):
    """Extrahiert WAV-Bytes und Sprache aus multipart/form-data."""
    m = re.search(r'boundary=([^\s;]+)', content_type)
    if not m:
        return None, LANG_DEFAULT
    boundary = m.group(1).strip('"\'')
    delim = ('--' + boundary).encode()

    audio = None
    lang  = LANG_DEFAULT

    for raw_part in body.split(delim):
        header_end = raw_part.find(b'\r\n\r\n')
        if header_end == -1:
            continue
        headers = raw_part[:header_end].decode('utf-8', errors='replace')
        content = raw_part[header_end + 4:].rstrip(b'\r\n-')

        name_m = re.search(r'name="([^"]+)"', headers)
        if not name_m:
            continue
        name = name_m.group(1)

        if name == 'file':
            audio = content
        elif name == 'language':
            lang = content.decode('utf-8', errors='replace').strip()

    return audio, lang


class InferenceHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # Kein HTTP-Access-Log

    def do_GET(self):
        if self.path == '/health':
            self._respond(200, {'status': 'ok'})
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path != '/inference':
            self.send_response(404)
            self.end_headers()
            return

        content_type = self.headers.get('Content-Type', '')
        length       = int(self.headers.get('Content-Length', 0))
        body         = self.rfile.read(length)

        audio_bytes, lang = parse_multipart(content_type, body)
        if not audio_bytes:
            self._respond(400, {'error': 'Kein Audio-Feld in Anfrage'})
            return

        tmp = None
        try:
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
                f.write(audio_bytes)
                tmp = f.name

            effective_lang = lang if lang and lang != 'auto' else 'de'
            segments, _ = model.transcribe(
                tmp,
                language=effective_lang,
                beam_size=1,
                vad_filter=True,
            )
            text = ' '.join(s.text for s in segments).strip()
            self._respond(200, {'text': text})

        except Exception as exc:
            self._respond(500, {'error': str(exc)})
        finally:
            if tmp and os.path.exists(tmp):
                os.unlink(tmp)

    def _respond(self, status: int, data: dict):
        payload = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == '__main__':
    server = HTTPServer(('127.0.0.1', PORT), InferenceHandler)
    print(f'[fw-server] Lauscht auf 127.0.0.1:{PORT}', flush=True)
    server.serve_forever()
