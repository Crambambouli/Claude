#!/usr/bin/env python3
"""
Persistenter Whisper-HTTP-Server für Blitztext.
Lädt das Modell einmalig in den RAM – danach ~0.5-1s pro Diktat statt ~4s.

Wird automatisch von der App gestartet – nicht manuell aufrufen.
"""
import sys, os, json, tempfile, threading
from http.server import HTTPServer, BaseHTTPRequestHandler

def load_model(model_size, device='cpu'):
    try:
        from faster_whisper import WhisperModel
        print(f"[whisper-server] Lade faster-whisper '{model_size}'...", flush=True)
        m = WhisperModel(model_size, device=device, compute_type='int8')
        print(f"[whisper-server] Modell bereit.", flush=True)
        return m, 'faster'
    except ImportError:
        pass

    try:
        import whisper
        print(f"[whisper-server] Lade openai-whisper '{model_size}'...", flush=True)
        m = whisper.load_model(model_size)
        print(f"[whisper-server] Modell bereit.", flush=True)
        return m, 'openai'
    except ImportError:
        pass

    print("[whisper-server] FEHLER: weder faster-whisper noch openai-whisper gefunden.", flush=True)
    sys.exit(1)


class Handler(BaseHTTPRequestHandler):
    model      = None
    model_type = 'faster'
    language   = None

    def do_GET(self):
        if self.path == '/health':
            self._ok(b'ok')

    def do_POST(self):
        if self.path != '/transcribe':
            self.send_response(404); self.end_headers(); return

        length     = int(self.headers.get('Content-Length', 0))
        audio_data = self.rfile.read(length)
        lang       = self.headers.get('X-Language') or Handler.language or None
        if lang == 'auto': lang = None

        tmp = None
        try:
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
                f.write(audio_data)
                tmp = f.name

            text = self._transcribe(tmp, lang)
            self._ok(json.dumps({'text': text}).encode())
        except Exception as e:
            self._err(str(e))
        finally:
            if tmp and os.path.exists(tmp):
                os.unlink(tmp)

    def _transcribe(self, path, lang):
        if Handler.model_type == 'faster':
            segs, _ = Handler.model.transcribe(
                path, language=lang, beam_size=5,
                vad_filter=True,
                vad_parameters=dict(min_silence_duration_ms=400, speech_pad_ms=200),
                condition_on_previous_text=False,
                no_speech_threshold=0.6,
            )
            return ' '.join(s.text.strip() for s in segs if s.text.strip())
        else:
            result = Handler.model.transcribe(
                path, language=lang,
                condition_on_previous_text=False,
                no_speech_threshold=0.6,
            )
            return result['text'].strip()

    def _ok(self, body):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _err(self, msg):
        body = json.dumps({'error': msg}).encode()
        self.send_response(500)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_): pass   # kein Logging


def main():
    model_size = sys.argv[1] if len(sys.argv) > 1 else 'base'
    port       = int(sys.argv[2]) if len(sys.argv) > 2 else 8765
    language   = sys.argv[3] if len(sys.argv) > 3 else None

    Handler.model, Handler.model_type = load_model(model_size)
    Handler.language = language

    server = HTTPServer(('127.0.0.1', port), Handler)
    print(f"[whisper-server] Lauscht auf Port {port}", flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
