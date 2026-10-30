package com.nowsteering.probe

/** Test strings. Items are prefixed A, B, C... so a missing or boxed glyph can be named from a photo. */
object Presets {
    private val ruler62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    // Each block of 10 ends with its own position, e.g. "....20|": the last number you see is the count.
    private val ruler120 = (1..12).joinToString("") { n -> val s = "${n * 10}|"; ".".repeat(10 - s.length) + s }

    val list: List<Pair<String, String>> = listOf(
        "Ruler 62 (0-9A-Za-z)" to ruler62,
        "Ruler 120 (decades)" to ruler120,
        "Ruler 1-20 digits" to "12345678901234567890",
        "ASCII arrows" to "A-> B<- C^ Dv E=> F<= G>> H<<",
        "Unicode arrows" to "A← B→ C↑ D↓ E↖ F↗ G⇐ H⇒ I➜ J⬆",
        "Box drawing" to "A─ B│ C┌ D┐ E└ F┘ G├ H┼ I═ J║",
        "Blocks" to "A█ B▓ C▒ D░ E▀ F▄ G▌ H▐ I■ J□",
        "Symbols" to "A° B• C● D▲ E▶ F▼ G◀ H★ I✓ J×",
        "Latin-1 / Win-1252" to "A° B± C² D½ E© F« G» H· I€ Jé",
        "ASCII punctuation" to "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~",
        "Devanagari (Hindi)" to "A नमस्ते B दिल्ली C 123",
        "Emoji" to "A🚗 B➡️ C🙂 D👍",
        "Spaces test" to "A  B   C    D|",
        "Plain words" to "Turn left in 200 m",
    )
}
