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
        "Devanagari + emoji" to "A नमस्ते B 🚗 C",
    )
}
