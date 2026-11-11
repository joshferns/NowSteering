package com.nowsteering.app

/** Test strings for the Diagnostics text tester. */
object Presets {
    private fun test(line1: String, line2: String, line3: String) =
        Screen.lines(line1, line2, line3, line2Max = 20, line3Max = 20, extra = Glyphs.TEST_EXTRA).copy(titleMax = 17)

    /** One-tap parked checks, shown top to bottom exactly as on the car (line 1 big, line 2 disc icon, line 3 person icon). */
    val testScreens: List<Pair<String, Screen>> = listOf(
        "♪ glyph" to test("♪ ♫ • ▶ ♪", "♪ Falling", "♪ Falling - Trevor"),
        "Glyph candidates" to test("♩ ♬ ♭ ♯ ♠", "♣ ♥ ♦ ☼ ♀", "♂ « » ○ ◊"),
        "Song widths" to test("Song 20 chars", "Falling - Trevor Dan", "MWMWMWMWMWMWMWMWMWMW"),
        "Line 2 text" to test("Line 2 text", "1 hr 59 min 11:59pm", "12 min · 19:42"),
        "Bars" to test("Bars", "12 min ███▒▒▒", "1:02:31 left ██▒▒▒▒"),
        "Mirror preview" to test("17:03  32 km/h", "▶ Falling", "2:31 left ███▒▒▒"),
    )

    val list: List<Pair<String, String>> = listOf(
        "Ruler 62" to "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
        "Nav sample" to "↖ 300 m",
        "Road sample" to "80 Feet Rd",
        "ETA sample" to "ETA 19:42 6.1km",
        "Confirmed glyphs" to "← → ↑ ↓ ↖ ↗ ° • ● ▲ ▶ ▼ █ ▓ ▒ ▄",
        "Fallback test" to "⇐ ⇒ ↘ ↙ ◀ ★ ✓ ─ ┼ ░ — … ₹",
        "Wide letters" to "WWWWWWWWWWWWWWWWWWWW",
        "Note glyph ♪" to "♪ ♫ • ▶",
        "Song with note" to "♪ Falling - Trevor",
        "Song line 20 chars" to "Falling - Trevor Dan",
        "Song line 20 wide" to "MWMWMWMWMWMWMWMWMWMW",
        "Line 2: time + bar" to "12 min ███▒▒▒",
        "Line 2: time + clock" to "12 min · 19:42",
        "Line 2: longest" to "1 hr 59 min 11:59pm",
        "Mirror: title" to "▶ Falling",
        "Mirror: time left" to "12:31 left ██▒▒▒▒",
        "Mirror: 1 h+ left" to "1:02:31 left ██▒▒▒▒",
        "Clock + speed" to "17:03  32 km/h",
        "Turn bar (10 cells)" to "█████▒▒▒▒▒",
        "Trip bar line" to "ETA 09:45 ███▒▒",
        "Trip bar line 12h" to "ETA 9:45pm ███▒▒",
        "18 full blocks" to "██████████████████",
        "18 light blocks" to "▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒",
        "Devanagari + emoji" to "A नमस्ते B 🚗 C",
    )
}
