package com.nowsteering.app

import java.text.Normalizer

/** What the Honda head unit can render (Phase 0), and ASCII fallbacks for everything else. */
object Glyphs {
    const val TITLE_MAX = CarProfile.HONDA_SAFE_TITLE
    const val LINE_MAX = CarProfile.HONDA_SAFE_LINE
    /** Glyphs the test screens and the free-form tester let through, to see which the car draws as a real symbol. */
    const val TEST_EXTRA = "♪♫♩♬♭♯♠♣♥♦☼♀♂○◊"
    /** Song line: the car measured 20 characters for artist/album in Phase 0 (the parked width test re-checks it). */
    const val SONG_MAX = CarProfile.HONDA_SONG_LINE

    /** Confirmed non-Latin-1 glyphs. ASCII and Latin-1 (U+00A0..U+00FF) are always allowed. */
    private const val CONFIRMED = CarProfile.HONDA_CONFIRMED

    private val fallback = mapOf(
        '⇐' to "<=", '⇒' to "=>", '⇑' to "^", '⇓' to "v",
        '↘' to "\\", '↙' to "/", '↰' to "←", '↱' to "→", '⬆' to "↑", '⬇' to "↓", '⬅' to "←", '➡' to "→", '➜' to "→",
        '◀' to "<", '►' to "▶", '▸' to "▶", '◂' to "<", '★' to "*", '☆' to "*", '✓' to "v", '✔' to "v",
        '─' to "-", '━' to "-", '│' to "|", '┃' to "|", '┌' to "+", '┐' to "+", '└' to "+", '┘' to "+", '├' to "+", '┤' to "+", '┼' to "+",
        '═' to "=", '║' to "|", '░' to ".", '▀' to "-", '▌' to "|", '▐' to "|", '■' to "█", '□' to "o",
        '–' to "-", '—' to "-", '‐' to "-", '−' to "-", '‘' to "'", '’' to "'", '‚' to ",", '“' to "\"", '”' to "\"",
        '…' to "...", '€' to "EUR", '₹' to "Rs", ' ' to " ", ' ' to " ", ' ' to " ",
    )

    private fun allowed(c: Char, extra: String) =
        c in ' '..'~' || c in '\u00A0'..'\u00FF' || CONFIRMED.indexOf(c) >= 0 || extra.indexOf(c) >= 0

    /**
     * Replace or drop unsupported characters, collapse whitespace, then cut to [max] characters.
     * [extra] lists glyphs the car is known to show beyond the confirmed set (e.g. "♪" once the parked test passed).
     */
    fun sanitize(input: String?, max: Int, extra: String = ""): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        for (c in input) {
            when {
                c == '\n' || c == '\t' || c == '\r' -> sb.append(' ')
                allowed(c, extra) -> sb.append(c)
                fallback.containsKey(c) -> sb.append(fallback[c])
                else -> {
                    // Accented letters outside Latin-1 (e.g. "ā") lose the accent; anything else is dropped.
                    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filter { allowed(it, extra) && it != ' ' }
                    sb.append(base)
                }
            }
        }
        val collapsed = sb.toString().replace(' ', ' ').replace(Regex(" {2,}"), " ").trim()
        if (collapsed.length <= max) return collapsed
        // Cut at a word boundary ("Glen's Bakehouse", not "Glen's Bakehouse S") unless that loses too much.
        val cut = collapsed.take(max + 1).lastIndexOf(' ')
        return if (cut >= max * 6 / 10) collapsed.take(cut).trimEnd() else collapsed.take(max).trimEnd()
    }
}

/**
 * One car screen. Top to bottom on the Honda: title (big line), album (disc icon), artist (person icon).
 * The briefs call these line 1, line 2 and line 3: use [lines] so the numbers map to the right field.
 */
data class Screen(
    val title: String,
    val artist: String,
    val album: String,
    val titleMax: Int = Glyphs.TITLE_MAX,
    val artistMax: Int = Glyphs.LINE_MAX,
    val albumMax: Int = Glyphs.LINE_MAX,
    val extra: String = "",
) {
    companion object {
        /** line1 = big line, line2 = disc-icon line (album field), line3 = person-icon line (artist field). */
        fun lines(line1: String, line2: String, line3: String, line2Max: Int = Glyphs.LINE_MAX,
                  line3Max: Int = Glyphs.LINE_MAX, extra: String = "") =
            Screen(line1, artist = line3, album = line2, artistMax = line3Max, albumMax = line2Max, extra = extra)
    }

    fun sanitized() = copy(
        title = Glyphs.sanitize(title, titleMax, extra),
        artist = Glyphs.sanitize(artist, artistMax, extra),
        album = Glyphs.sanitize(album, albumMax, extra),
    )

    override fun toString() = "'$title' / '$album' / '$artist'"
}
