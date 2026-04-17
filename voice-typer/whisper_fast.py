#!/usr/bin/env python3
"""
faster-whisper CLI-Wrapper mit openai-whisper-kompatiblem Interface.
Nutzung in Blitztext: whisper_fast.py als Whisper-Pfad in den Einstellungen eintragen.

Installation: pip install faster-whisper
"""
import sys
import os
import argparse

def main():
    parser = argparse.ArgumentParser(description="faster-whisper CLI wrapper")
    parser.add_argument("audio",                   help="Audiodatei")
    parser.add_argument("--model",      default="base",  help="Modell (tiny/base/small/medium/large-v3)")
    parser.add_argument("--output_dir", default=".",     help="Ausgabeverzeichnis")
    parser.add_argument("--output_format", default="txt", help="Ausgabeformat (nur txt unterstützt)")
    parser.add_argument("--language",   default=None,    help="Sprache (de/en/auto)")
    parser.add_argument("--task",       default="transcribe")
    # openai-whisper-Flags die wir ignorieren (Kompatibilität)
    parser.add_argument("--condition_on_previous_text",    default="False")
    parser.add_argument("--no_speech_threshold",           default="0.6",  type=float)
    parser.add_argument("--logprob_threshold",             default="-1.0", type=float)
    parser.add_argument("--compression_ratio_threshold",   default="2.4",  type=float)
    parser.add_argument("--fp16",       default="False")

    args, _ = parser.parse_known_args()

    try:
        from faster_whisper import WhisperModel
    except ImportError:
        print("FEHLER: faster-whisper nicht installiert.", file=sys.stderr)
        print("Bitte ausführen: pip install faster-whisper", file=sys.stderr)
        sys.exit(1)

    language = None if (not args.language or args.language == "auto") else args.language

    # Modell laden (wird beim ersten Aufruf gecacht)
    model = WhisperModel(
        args.model,
        device="cpu",
        compute_type="int8",   # schneller, weniger RAM
    )

    # Transkription mit VAD-Filter (eliminiert Stille-Halluzinationen)
    segments, _info = model.transcribe(
        args.audio,
        language=language,
        task=args.task,
        beam_size=5,
        vad_filter=True,                    # ← löst ZDF-Halluzinationen
        vad_parameters=dict(
            min_silence_duration_ms=500,
            speech_pad_ms=200,
        ),
        condition_on_previous_text=False,   # ← verhindert Halluzinations-Schleifen
        no_speech_threshold=args.no_speech_threshold,
        log_prob_threshold=args.logprob_threshold,
        compression_ratio_threshold=args.compression_ratio_threshold,
    )

    # Text zusammensetzen
    lines = [seg.text.strip() for seg in segments if seg.text.strip()]
    transcript = " ".join(lines)

    # In Ausgabedatei schreiben (openai-whisper-kompatibel: <audioname>.txt)
    base = os.path.splitext(os.path.basename(args.audio))[0]
    out_path = os.path.join(args.output_dir, base + ".txt")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(transcript)

    # Auch auf stdout ausgeben (Fallback in whisper.ts)
    print(transcript, flush=True)

if __name__ == "__main__":
    main()
