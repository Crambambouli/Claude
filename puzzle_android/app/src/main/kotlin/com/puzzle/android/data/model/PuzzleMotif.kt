package com.puzzle.android.data.model

enum class PuzzleCategory(val label: String, val emoji: String, val englishPrompt: String) {
    BLUMEN   ("Blumen",   "🌸", "close-up of a vibrant flower bouquet with roses tulips and daisies, colorful garden blooms, bright petals, spring flowers, no water no sea"),
    TIERE    ("Tiere",    "🦁", "majestic lion portrait in african savanna, wildlife photography, golden hour, animals in nature, no water no ocean"),
    NATUR    ("Natur",    "🌲", "dense green forest with tall pine trees and mountain peaks, lush vegetation, alpine landscape, no water no sea no ocean no coast"),
    STAEDTE  ("Städte",   "🏙️", "modern city skyline with skyscrapers at golden hour, urban architecture, city lights, buildings, no water no sea"),
    ABSTRAKT ("Abstrakt", "🎨", "abstract colorful geometric shapes and swirls, vibrant kaleidoscope pattern, bold colors, no figures no landscape"),
    FANTASIE ("Fantasie", "✨", "magical fantasy castle in enchanted forest with glowing fairy lights, mystical creatures, dreamy atmosphere, no water no sea")
}

enum class PuzzleStyle(val label: String, val englishPrompt: String) {
    BUNT      ("Bunt",     "vibrant colorful bright saturated"),
    PASTELL   ("Pastell",  "soft pastel gentle muted colors"),
    AQUARELL  ("Aquarell", "watercolor painting artistic style"),
    GEMAELDE  ("Gemälde",  "classic oil painting style detailed")
}
