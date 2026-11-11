package com.nowsteering.app

import java.util.Locale

/** What goes in front of the song on the nav screen. ▶ is proven on the car; the car shows ♪ ♫ as dots. */
enum class SongPrefix(val glyph: String, val label: String) {
    ARROW("▶", "▶ (play, default)"),
    NONE("", "none"),
    DOT("•", "• (dot)"),
    NOTE("♪", "♪ (shows as a dot on this car)");

    /** Extra glyph the car must be known to show, passed to [Glyphs.sanitize]. */
    val extra: String get() = if (this == NOTE) "♪" else ""

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ARROW
    }
}

/** The car's lines, as pure functions. Line 1 = big line, line 2 = disc-icon line, line 3 = person-icon line. */
object Layouts {
    /** "Title - Artist" for the song line, or null when nothing readable is left after dropping unsupported characters. */
    fun songText(title: String?, artist: String?, prefix: SongPrefix): String? {
        val t = (title ?: "").trim()
        val a = (artist ?: "").trim()
        if (t.isEmpty()) return null
        val body = if (a.isEmpty() || t.contains(a, ignoreCase = true)) t else "$t - $a"
        val clean = Glyphs.sanitize(body, 80, prefix.extra)
        if (clean.count { it.isLetterOrDigit() } < 2) return null // e.g. a title in Devanagari or emoji only
        return if (prefix == SongPrefix.NONE) clean else "${prefix.glyph} $clean"
    }

    /**
     * Line 2 while far from a turn: Maps' own time left plus a trip-progress bar, e.g. "12 min ███▒▒▒".
     * With [showArrival] the arrival clock replaces the bar ("12 min · 19:42"): both do not fit on one line.
     */
    fun farLine2(timeLeft: String, distanceLeft: String, arrival: String, tripFraction: Double?,
                 showArrival: Boolean, showBar: Boolean): String {
        val t = timeLeft.ifEmpty { distanceLeft.replace(" ", "") }
        if (showArrival && arrival.isNotEmpty()) {
            if (t.isEmpty()) return "ETA $arrival"
            val dotted = "$t · $arrival"
            return if (dotted.length <= Glyphs.LINE_MAX) dotted else "$t $arrival"
        }
        val bar = if (showBar && tripFraction != null) Bars.bar(tripFraction, Bars.TRIP_CELLS) else null
        return listOfNotNull(t.ifEmpty { null }, bar).joinToString(" ")
    }

    /** "2:31" or "1:02:31". */
    fun mmss(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** Line 3 of the music-mirror screen: "2:31 left ███▒▒▒", or the artist when the length is unknown (live streams). */
    fun mirrorLine3(durationMs: Long, positionMs: Long?, artist: String): String {
        val known = durationMs in 1_000..86_400_000 && positionMs != null && positionMs >= 0
        if (!known) return artist
        val pos = positionMs!!.coerceAtMost(durationMs)
        return "${mmss(durationMs - pos)} left ${Bars.bar(pos.toDouble() / durationMs, Bars.TRIP_CELLS)}"
    }
}

/**
 * Near / far from the next turn. Enters "near" at 300 m, leaves it only above 340 m (or on a new instruction),
 * so a distance wobbling around 300 m cannot flip the layout back and forth.
 */
class TurnZone(private val enterM: Double = Bars.TURN_BAR_FROM_M, private val leaveM: Double = enterM + 40) {
    var near = false
        private set

    fun update(metersToTurn: Double?): Boolean {
        near = when {
            metersToTurn == null -> false
            near -> metersToTurn <= leaveM
            else -> metersToTurn <= enterM
        }
        return near
    }

    fun reset() { near = false }
}
