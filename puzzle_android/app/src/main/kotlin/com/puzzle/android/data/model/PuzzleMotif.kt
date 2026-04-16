package com.puzzle.android.data.model

enum class PuzzleCategory(val label: String, val emoji: String, val englishPrompt: String) {
    BLUMEN   ("Blumen",   "🌸", "colorful flowers bouquet garden"),
    TIERE    ("Tiere",    "🦁", "cute animals wildlife nature"),
    NATUR    ("Natur",    "🌲", "nature landscape mountains forest"),
    STAEDTE  ("Städte",   "🏙️", "cityscape skyline architecture"),
    ABSTRAKT ("Abstrakt", "🎨", "abstract geometric colorful shapes"),
    FANTASIE ("Fantasie", "✨", "fantasy magical world fairy tale")
}

enum class PuzzleStyle(val label: String, val englishPrompt: String) {
    BUNT      ("Bunt",     "vibrant colorful bright"),
    PASTELL   ("Pastell",  "soft pastel gentle colors"),
    AQUARELL  ("Aquarell", "watercolor painting style"),
    GEMAELDE  ("Gemälde",  "oil painting classic style")
}
