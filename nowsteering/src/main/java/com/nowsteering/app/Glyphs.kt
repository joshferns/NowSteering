package com.nowsteering.app

import java.text.Normalizer

/** What the Honda head unit can render (Phase 0), and ASCII fallbacks for everything else. */
object Glyphs {
    const val TITLE_MAX = 15
    const val LINE_MAX = 18

    /** Confirmed non-Latin-1 glyphs. ASCII and Latin-1 (U+00A0..U+00FF) are always allowed. */
    private const val CONFIRMED = "←→↑↓↖↗•●▲▶▼█▓▒▄"

    private val fallback = mapOf(
        '⇐' to "<=", '⇒' to "=>", '⇑' to "^", '⇓' to "v",
        '↘' to "\\", '↙' to "/", '↰' to "←", '↱' to "→", '⬆' to "↑", '⬇' to "↓", '⬅' to "←", '➡' to "→", '➜' to "→",
        '◀' to "<", '►' to "▶", '▸' to "▶", '◂' to "<", '★' to "*", '☆' to "*", '✓' to "v", '✔' to "v",
        '─' to "-", '━' to "-", '│' to "|", '┃' to "|", '┌' to "+", '┐' to "+", '└' to "+", '┘' to "+", '├' to "+", '┤' to "+", '┼' to "+",
        '═' to "=", '║' to "|", '░' to ".", '▀' to "-", '▌' to "|", '▐' to "|", '■' to "█", '□' to "o",
        '–' to "-", '—' to "-", '‐' to "-", '−' to "-", '‘' to "'", '’' to "'", '‚' to ",", '“' to "\"", '”' to "\"",
        '…' to "...", '€' to "EUR", '₹' to "Rs", ' ' to " ", ' ' to " ", ' ' to " ",
    )

    private fun allowed(c: Char) = c in ' '..'~' || c in ' '..'ÿ' || CONFIRMED.indexOf(c) >= 0

    /** Replace or drop unsupported characters, collapse whitespace, then cut to [max] characters. */
    fun sanitize(input: String?, max: Int): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        for (c in input) {
            when {
                c == '\n' || c == '\t' || c == '\r' -> sb.append(' ')
                allowed(c) -> sb.append(c)
                fallback.containsKey(c) -> sb.append(fallback[c])
                else -> {
                    // Accented letters outside Latin-1 (e.g. "ā") lose the accent; anything else is dropped.
                    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).filter { allowed(it) && it != ' ' }
                    sb.append(base)
                }
            }
        }
        val collapsed = sb.toString().replace(' ', ' ').replace(Regex(" {2,}"), " ").trim()
        return collapsed.take(max).trimEnd()
    }
}

/** One car screen: big line, person-icon line, disc-icon line. */
data class Screen(val title: String, val artist: String, val album: String) {
    fun sanitized() = Screen(
        Glyphs.sanitize(title, Glyphs.TITLE_MAX),
        Glyphs.sanitize(artist, Glyphs.LINE_MAX),
        Glyphs.sanitize(album, Glyphs.LINE_MAX),
    )

    override fun toString() = "'$title' / '$artist' / '$album'"
}
