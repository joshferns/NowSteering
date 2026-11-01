package com.nowsteering.app

/** Text parts of Google Maps' navigation notification, parsed defensively. Pure Kotlin for unit tests. */
data class NavText(
    val distance: String,     // to the next maneuver, e.g. "300 m"
    val road: String,         // e.g. "80 Feet Rd"
    val eta: String,          // e.g. "19:42"
    val remaining: String,    // distance left, e.g. "6.1 km"
    val textGlyph: String?,   // maneuver from instruction words, if any
)

object NavTextParser {
    private val distanceRe = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val timeRe = Regex("""\b(\d{1,2}[:.]\d{2})\s?([ap]\.?\s?m\.?)?""", RegexOption.IGNORE_CASE)

    /**
     * title    = android.title    e.g. "300 m"
     * text     = android.text     e.g. "80 Feet Rd towards Indiranagar"
     * subText  = android.subText  e.g. "13 min · 6.1 km · 19:42 ETA"
     */
    fun parse(title: String?, text: String?, subText: String?, bigText: String? = null): NavText {
        val t = clean(title)
        val x = clean(text)
        val sub = clean(subText)

        val distance = distanceRe.find(t)?.let { fmtDistance(it) } ?: ""

        // Road: the part before "towards"; "Towards X" alone becomes "to X".
        var road = x
        val towards = Regex("""\s+towards?\s+""", RegexOption.IGNORE_CASE)
        if (towards.containsMatchIn(road)) road = road.split(towards, 2)[0]
        if (road.isEmpty() && distance.isEmpty()) road = t // e.g. "Rerouting…" or an instruction in the title
        // "15th Cross Rd / 1st Main Rd" or "Glen's Bakehouse / No. 552, Main Rd": the first name is enough.
        road = road.split(" / ", limit = 2)[0].trim()
        road = road.replace(Regex("""^towards?\s+""", RegexOption.IGNORE_CASE), "to ")
        road = road.replace(Regex("""^(turn|keep|bear|slight|sharp|continue|head|make|take|merge)\b.*?\b(onto|on|to)\s+""", RegexOption.IGNORE_CASE), "")

        // subText parts are separated by "·"; take ETA and the distance-left part.
        val parts = sub.split('·', '•').map { it.trim() }.filter { it.isNotEmpty() }
        val etaPart = parts.firstOrNull { it.contains("ETA", true) || it.contains("arrival", true) } ?: parts.lastOrNull { timeRe.containsMatchIn(it) }
        val eta = etaPart?.let { p -> timeRe.find(p)?.let { m -> m.groupValues[1].replace('.', ':') + (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { ap -> ap.filter { it.isLetter() }.lowercase() } ?: "") } } ?: ""
        val remaining = parts.firstNotNullOfOrNull { p -> distanceRe.find(p)?.let { fmtDistance(it) } } ?: ""

        val words = listOf(t, x, clean(bigText)).joinToString(" ").lowercase()
        return NavText(distance, road, eta, remaining, glyphFromWords(words))
    }

    /**
     * Maps alternates the real instruction ("170 m" / "Kodigehalli Main Rd") with a heading variant
     * (empty or "0 m" title, "towards 2nd Cross Rd" text). Once a real instruction exists the variant
     * only causes flicker. Arrival ("" / place name) and "Rerouting..." are not variants.
     */
    fun isHeadingVariant(title: String?, text: String?): Boolean {
        val t = clean(title)
        val x = clean(text)
        val noDistance = t.isEmpty() || Regex("""^0 (m|ft|yd)$""").matches(t)
        val towards = Regex("""(^|\s)towards?\s""", RegexOption.IGNORE_CASE).containsMatchIn(x)
        return noDistance && towards
    }

    /** "300 m" -> 300.0, "1.2 km" -> 1200.0; null for imperial units or anything else. */
    fun metersOf(distance: String): Double? {
        val m = Regex("""^(\d+(?:[.,]\d+)?) (m|km)$""").find(distance.trim()) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (m.groupValues[2] == "km") v * 1000 else v
    }

    /** Formats like Maps does: 10 m steps below 300 m, 50 m steps below 1 km, then 0.1 km. */
    fun formatMeters(m: Double): String = when {
        m <= 0 -> "0 m"
        m < 300 -> "${(Math.round(m / 10) * 10)} m"
        m < 1000 -> "${(Math.round(m / 50) * 50).coerceAtMost(950)} m"
        else -> String.format(java.util.Locale.US, "%.1f km", m / 1000)
    }

    private fun clean(s: String?) = (s ?: "").replace(' ', ' ').replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    private fun fmtDistance(m: MatchResult) = "${m.groupValues[1]} ${m.groupValues[2].lowercase()}"

    /** Maneuver words in the instruction text (Maps usually puts the maneuver only in the icon). */
    fun glyphFromWords(w: String): String? = when {
        Regex("""\bu-?turn\b|make a u\b""").containsMatchIn(w) -> "U"
        Regex("""\broundabout\b""").containsMatchIn(w) -> "O"
        Regex("""\b(destination|arrive|arrived)\b""").containsMatchIn(w) -> "●"
        Regex("""\b(slight|slightly|keep|bear|fork)\s+left\b|\bexit\s+left\b""").containsMatchIn(w) -> "↖"
        Regex("""\b(slight|slightly|keep|bear|fork)\s+right\b|\bexit\s+right\b""").containsMatchIn(w) -> "↗"
        Regex("""\b(turn|sharp)\s+left\b""").containsMatchIn(w) -> "←"
        Regex("""\b(turn|sharp)\s+right\b""").containsMatchIn(w) -> "→"
        Regex("""\b(continue|head|go straight|straight)\b""").containsMatchIn(w) -> "↑"
        else -> null
    }
}
