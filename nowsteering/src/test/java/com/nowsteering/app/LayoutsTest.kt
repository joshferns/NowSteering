package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    @Test fun songTextJoinsTitleAndArtist() {
        assertEquals("Falling - Trevor Daniel", Layouts.songText("Falling", "Trevor Daniel", SongPrefix.NONE))
        assertEquals("Falling", Layouts.songText("Falling", "", SongPrefix.NONE))
        assertEquals("Trevor Daniel - Falling", Layouts.songText("Trevor Daniel - Falling", "Trevor Daniel", SongPrefix.NONE)) // no repeat
        assertNull(Layouts.songText("", "Trevor Daniel", SongPrefix.NONE))
        assertNull(Layouts.songText(null, null, SongPrefix.NONE))
    }

    @Test fun songTextPrefixAndUnreadableTitles() {
        assertEquals("• Falling - Trevor Daniel", Layouts.songText("Falling", "Trevor Daniel", SongPrefix.DOT))
        assertEquals("♪ Falling", Layouts.songText("Falling", "", SongPrefix.NOTE))
        assertNull(Layouts.songText("नमस्ते", "", SongPrefix.NONE))   // dropped to nothing: show the road instead
        assertNull(Layouts.songText("🚗🚗", "", SongPrefix.NONE))
        assertEquals("Mix 2", Layouts.songText("Mix 2 🚗", "", SongPrefix.NONE))
    }

    @Test fun songLineFitsTwentyCharactersAtAWord() {
        val s = Screen.lines("x", "y", Layouts.songText("Falling", "Trevor Daniel", SongPrefix.NONE)!!, line3Max = Glyphs.SONG_MAX).sanitized()
        assertEquals("Falling - Trevor", s.artist)            // word boundary, not "Falling - Trevor Dan"
        assertTrue(s.artist.length <= 20)
        val note = Screen.lines("x", "y", Layouts.songText("Falling", "Trevor Daniel", SongPrefix.NOTE)!!,
            line3Max = Glyphs.SONG_MAX, extra = SongPrefix.NOTE.extra).sanitized()
        assertEquals("♪ Falling - Trevor", note.artist)
    }

    @Test fun noteGlyphIsDroppedUnlessAllowed() {
        assertEquals("Falling", Glyphs.sanitize("♪ Falling", 20))
        assertEquals("♪ Falling", Glyphs.sanitize("♪ Falling", 20, "♪"))
    }

    @Test fun farLine2() {
        assertEquals("12 min ███▒▒▒", Layouts.farLine2("12 min", "6.1 km", "19:42", 0.5, showArrival = false, showBar = true))
        assertEquals("12 min", Layouts.farLine2("12 min", "6.1 km", "19:42", 0.5, showArrival = false, showBar = false))
        assertEquals("12 min · 19:42", Layouts.farLine2("12 min", "6.1 km", "19:42", 0.5, showArrival = true, showBar = true))
        assertEquals("1 hr 5 min 7:42pm", Layouts.farLine2("1 hr 5 min", "41 km", "7:42pm", 0.5, showArrival = true, showBar = true))
        assertEquals("6.1km ███▒▒▒", Layouts.farLine2("", "6.1 km", "19:42", 0.5, showArrival = false, showBar = true)) // no time from Maps
        assertEquals("12 min", Layouts.farLine2("12 min", "6.1 km", "", null, showArrival = false, showBar = true))
        // The longest realistic lines: 18 without the arrival clock, 19 with it (the car measured 20 for this field).
        assertEquals(18, Layouts.farLine2("1 hr 59 min", "", "", 0.9, false, true).length)
        assertEquals("1 hr 59 min 11:59pm", Layouts.farLine2("1 hr 59 min", "", "11:59pm", 0.9, true, true))
    }

    @Test fun mirrorLine3() {
        assertEquals("2:31 left ███▒▒▒", Layouts.mirrorLine3(300_000, 149_000, "Artist"))
        assertEquals("1:02:31 left ▒▒▒▒▒▒", Layouts.mirrorLine3(3_751_000, 0, "Artist"))
        assertEquals("0:00 left ██████", Layouts.mirrorLine3(180_000, 999_999, "Artist")) // position past the end
        assertEquals("Kenny Sebastian", Layouts.mirrorLine3(0, 5_000, "Kenny Sebastian"))   // live: duration unknown
        assertEquals("Kenny Sebastian", Layouts.mirrorLine3(180_000, null, "Kenny Sebastian"))
        assertEquals("Kenny Sebastian", Layouts.mirrorLine3(Long.MAX_VALUE, 5_000, "Kenny Sebastian"))
    }

    @Test fun mirrorScreenFitsTheCar() {
        val s = Screen.lines("17:03  32 km/h", "▶ Falling", Layouts.mirrorLine3(300_000, 149_000, "x"), 20, 20).sanitized()
        assertEquals("17:03 32 km/h", s.title)   // the engine collapses double spaces
        assertEquals("▶ Falling", s.album)
        assertEquals("2:31 left ███▒▒▒", s.artist)
        // lines() maps line2 to the album field and line3 to the artist field
        assertEquals("L2", Screen.lines("L1", "L2", "L3").album)
        assertEquals("L3", Screen.lines("L1", "L2", "L3").artist)
    }

    @Test fun testScreensReachTheCarUncut() {
        Presets.testScreens.forEach { (name, screen) ->
            val sent = screen.sanitized()
            assertEquals("$name line 2", screen.album, sent.album)    // nothing cut, nothing dropped (♪ included)
            assertEquals("$name line 3", screen.artist, sent.artist)
        }
        val widths = Presets.testScreens.first { it.first == "Song widths" }.second
        assertEquals(20, widths.album.length)
        assertEquals(20, widths.artist.length)
        assertEquals("♪ Falling - Trevor", Presets.testScreens.first { it.first == "♪ glyph" }.second.sanitized().artist)
    }

    @Test fun turnZoneHasHysteresis() {
        val z = TurnZone()
        assertFalse(z.update(450.0))
        assertTrue(z.update(300.0))
        assertTrue(z.update(320.0))   // wobble above 300 stays near
        assertFalse(z.update(341.0))
        assertFalse(z.update(310.0))  // far again: must re-enter at 300
        assertTrue(z.update(295.0))
        z.reset()
        assertFalse(z.near)
        assertFalse(z.update(null))
    }
}
