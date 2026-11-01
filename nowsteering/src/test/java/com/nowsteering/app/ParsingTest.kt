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

    @Test fun keepsFirstNameOfSlashedRoads() {
        assertEquals("15th Cross Rd", NavTextParser.parse("10 m", "15th Cross Rd / 1st Main Rd", "").road)
        assertEquals("Glen’s Bakehouse Sahakarnagar",
            NavTextParser.parse("550 m", "Glen’s Bakehouse Sahakarnagar  / No. 552, Sahakarnagar Main Rd", "").road)
    }

    @Test fun distanceRoundTrip() {
        assertEquals(300.0, NavTextParser.metersOf("300 m")!!, 0.01)
        assertEquals(1200.0, NavTextParser.metersOf("1.2 km")!!, 0.01)
        assertNull(NavTextParser.metersOf("0.3 mi"))
        assertEquals("170 m", NavTextParser.formatMeters(173.0))
        assertEquals("650 m", NavTextParser.formatMeters(640.0))
        assertEquals("950 m", NavTextParser.formatMeters(990.0))
        assertEquals("1.9 km", NavTextParser.formatMeters(1940.0))
        assertEquals("0 m", NavTextParser.formatMeters(-5.0))
    }

    @Test fun headingVariantDetection() {
        assertEquals(true, NavTextParser.isHeadingVariant("", "towards 2nd Cross Rd"))
        assertEquals(true, NavTextParser.isHeadingVariant("0 m", "Railway Parallel Rd towards Sahakar Nagar Railway Upas Rd"))
        assertEquals(false, NavTextParser.isHeadingVariant("170 m", "Kodigehalli Main Rd"))
        assertEquals(false, NavTextParser.isHeadingVariant("2.2 km", "towards Sardar Vallabhbhai Patel Rd"))
        assertEquals(false, NavTextParser.isHeadingVariant("", "Glen’s Bakehouse Sahakarnagar")) // arrival
        assertEquals(false, NavTextParser.isHeadingVariant("", "Rerouting..."))
        assertEquals(false, NavTextParser.isHeadingVariant("0 m", "16th E Cross Rd"))
    }

    @Test fun cutsLongNamesAtWords() {
        assertEquals("Glen's Bakehouse", Glyphs.sanitize("Glen’s Bakehouse Sahakarnagar", 18))
        assertEquals("Kodigehalli Main", Glyphs.sanitize("Kodigehalli Main Rd", 18))
        assertEquals("Sahakarnagarmainro", Glyphs.sanitize("Sahakarnagarmainroad", 18)) // no space: hard cut
        assertEquals("13th Main Rd", Glyphs.sanitize("13th Main Rd", 18))
    }

    @Test fun bars() {
        assertNull(Bars.turnBar(450.0))
        assertEquals("▒▒▒▒▒▒▒▒▒▒", Bars.turnBar(300.0))
        assertEquals("█████▒▒▒▒▒", Bars.turnBar(150.0))
        assertEquals("██████████", Bars.turnBar(0.0))
        assertEquals("███▒▒", Bars.tripBar(300.0, 750.0))
        assertNull(Bars.tripBar(null, 750.0))
        // Trip-bar ETA line fits the 18-char album line, also in 12-hour format.
        assertEquals("ETA 09:45 ███▒▒", Glyphs.sanitize("ETA 09:45 " + Bars.tripBar(300.0, 750.0), Glyphs.LINE_MAX))
        assertEquals("ETA 9:45pm ███▒▒", Glyphs.sanitize("ETA 9:45pm " + Bars.tripBar(300.0, 750.0), Glyphs.LINE_MAX))
    }

    @Test fun survivesGarbage() {
        val n = NavTextParser.parse(null, null, null)
        assertEquals("", n.distance); assertEquals("", n.road); assertEquals("", n.eta)
        val r = NavTextParser.parse("Rerouting…", "", "")
        assertEquals("Rerouting…", r.road)
    }
}
