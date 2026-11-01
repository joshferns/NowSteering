package com.nowsteering.app

/** Test strings for the Diagnostics text tester. */
object Presets {
    val list: List<Pair<String, String>> = listOf(
        "Ruler 62" to "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
        "Nav sample" to "↖ 300 m",
        "Road sample" to "80 Feet Rd",
        "ETA sample" to "ETA 19:42 6.1km",
        "Confirmed glyphs" to "← → ↑ ↓ ↖ ↗ ° • ● ▲ ▶ ▼ █ ▓ ▒ ▄",
        "Fallback test" to "⇐ ⇒ ↘ ↙ ◀ ★ ✓ ─ ┼ ░ — … ₹",
        "Wide letters" to "WWWWWWWWWWWWWWWWWWWW",
        "Turn bar (10 cells)" to "█████▒▒▒▒▒",
        "Trip bar line" to "ETA 09:45 ███▒▒",
        "Trip bar line 12h" to "ETA 9:45pm ███▒▒",
        "18 full blocks" to "██████████████████",
        "18 light blocks" to "▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒",
        "Devanagari + emoji" to "A नमस्ते B 🚗 C",
    )
}
