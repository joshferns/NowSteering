package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {
    @Test fun sanitizeKeepsConfirmedGlyphs() {
        assertEquals("↖ 300 m", Glyphs.sanitize("↖ 300 m", 15))
        assertEquals("° • ● ▲ ▶ ▼ █ ▓ ▒ ▄", Glyphs.sanitize("° • ● ▲ ▶ ▼ █ ▓ ▒ ▄", 40))
        assertEquals("Café", Glyphs.sanitize("Café", 15))
    }

    @Test fun sanitizeFallsBack() {
        assertEquals("<= => - +", Glyphs.sanitize("⇐ ⇒ ─ ┼", 20))
        assertEquals("A B", Glyphs.sanitize("A 🚗 B", 20))
        assertEquals("Rd", Glyphs.sanitize("नमस्ते Rd", 20))
        assertEquals("Mahadevapura", Glyphs.sanitize("Mahādēvapura", 20))
        assertEquals("7:42 pm", Glyphs.sanitize("7:42 pm", 20))
    }

    @Test fun sanitizeTruncates() {
        assertEquals("0123456789ABCDE", Glyphs.sanitize("0123456789ABCDEFGHIJ", 15))
        assertEquals("abc", Glyphs.sanitize("abc       def", 4))
    }

    @Test fun parsesTypicalMaps() {
        val n = NavTextParser.parse("300 m", "80 Feet Rd towards Indiranagar", "13 min · 6.1 km · 19:42 ETA")
        assertEquals("300 m", n.distance)
        assertEquals("80 Feet Rd", n.road)
        assertEquals("19:42", n.eta)
        assertEquals("6.1 km", n.remaining)
        assertNull(n.textGlyph)
    }

    @Test fun parsesTwelveHourAndTowards() {
        val n = NavTextParser.parse("1.2 km", "Towards MG Road", "1 hr 5 min · 41 km · 7:42 pm ETA")
        assertEquals("1.2 km", n.distance)
        assertEquals("to MG Road", n.road)
        assertEquals("7:42pm", n.eta)
        assertEquals("41 km", n.remaining)
    }

    @Test fun parsesInstructionWords() {
        val n = NavTextParser.parse("Turn left onto Church St", "", "")
        assertEquals("←", n.textGlyph)
        assertEquals("Church St", n.road)
        assertEquals("↗", NavTextParser.glyphFromWords("keep right at the fork"))
        assertEquals("U", NavTextParser.glyphFromWords("make a u-turn"))
    }

    @Test fun survivesGarbage() {
        val n = NavTextParser.parse(null, null, null)
        assertEquals("", n.distance); assertEquals("", n.road); assertEquals("", n.eta)
        val r = NavTextParser.parse("Rerouting…", "", "")
        assertEquals("Rerouting…", r.road)
    }
}
