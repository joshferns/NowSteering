package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTest {
    private fun fields(vararg kv: Pair<String, FieldValue>, neverShow: Set<String> = emptySet()): Fields {
        val m = kv.toMap()
        return Fields(neverShow) { m[it] }
    }
    private fun v(t: String, n: Double? = null, age: Long = 0) = FieldValue(t, n, "", age)

    @Test fun fillsFields() {
        assertEquals("32 km/h  17:03", Template.render("{speed} km/h  {clock}", fields("speed" to v("32"), "clock" to v("17:03"))))
    }

    @Test fun missingFieldDropsItsWholePart() {
        // "km/h" goes with the speed; no dash, no orphan unit.
        assertEquals("17:03", Template.render("{speed} km/h  {clock}", fields("clock" to v("17:03"))))
        assertEquals("32 km/h", Template.render("{speed} km/h  {clock}", fields("speed" to v("32"))))
        assertEquals("", Template.render("{speed} km/h  {clock}", fields()))
    }

    @Test fun fallbackTextKeepsThePart() {
        assertEquals("17:03  -- km/h", Template.render("{clock}  {speed|--} km/h", fields("clock" to v("17:03"))))
    }

    @Test fun singleSpacesStayOnePart() {
        // "▶ {song.title} - {song.artist}" is one part: no artist means the alternative is dropped, so a line lists a fallback.
        assertEquals("", Template.render("▶ {song.title} - {song.artist}", fields("song.title" to v("Falling"))))
        val line = Line(listOf(Alt("▶ {song.title} - {song.artist}"), Alt("▶ {song.title}")))
        assertEquals("▶ Falling", line.render(fields("song.title" to v("Falling")), 18).first)
        assertEquals("▶ Falling - Trevor", line.render(fields("song.title" to v("Falling"), "song.artist" to v("Trevor")), 18).first)
    }

    @Test fun barsTakeAWidthInCells() {
        assertEquals("2:31 left ███▒▒▒", Template.render("{song.time_left} left {bar song.progress 6}",
            fields("song.time_left" to v("2:31"), "song.progress" to v("50", 0.5))))
        assertEquals("▒▒▒▒", Template.render("{bar p 4}", fields("p" to v("0", 0.0))))
        assertEquals("", Template.render("{bar p 4}", fields()))
    }

    @Test fun staleFieldsAreMissing() {
        val f = fields("car.coolant" to v("84", 84.0, age = 10_000))
        assertNull(f.get("car.coolant"))
        assertEquals("", Template.render("{car.coolant}°C", f))
        assertEquals("84°C", Template.render("{car.coolant}°C", fields("car.coolant" to v("84", 84.0, age = 9_999))))
    }

    @Test fun neverShowFieldsAreMissingWhateverTheSourceSays() {
        assertEquals("", Template.render("{car.fuel_level}%", fields("car.fuel_level" to v("2.75"), neverShow = setOf("car.fuel_level"))))
    }

    @Test fun blankValuesAreMissing() {
        assertEquals("", Template.render("{turn.road}", fields("turn.road" to v(""))))
    }

    @Test fun firstNonEmptyAlternativeWins() {
        val line = Line(listOf(Alt("{a} {b}"), Alt("{a}"), Alt("{b}")))
        assertEquals("1 2", line.render(fields("a" to v("1"), "b" to v("2")), 18).first)
        assertEquals("1", line.render(fields("a" to v("1")), 18).first)
        assertEquals("2", line.render(fields("b" to v("2")), 18).first)
        assertEquals("", line.render(fields(), 18).first)
    }

    @Test fun fitsInSkipsALongAlternative() {
        val line = Line(listOf(Alt("{t} · {e}", 20, fitsIn = 18), Alt("{t} {e}", 20)))
        assertEquals("12 min · 19:42", line.render(fields("t" to v("12 min"), "e" to v("19:42")), 18).first)
        assertEquals("1 hr 59 min 11:59pm", line.render(fields("t" to v("1 hr 59 min"), "e" to v("11:59pm")), 18).first)
    }

    @Test fun lineWidthComesFromTheChosenAlternative() {
        val line = Line(listOf(Alt("▶ {song.text}", 20), Alt("{road}", 18)))
        assertEquals(20, line.render(fields("song.text" to v("x")), 15).second)
        assertEquals(18, line.render(fields("road" to v("y")), 15).second)
        assertEquals(15, Line("{nothing}").render(fields(), 15).second)
    }

    @Test fun faceTextIsCutAtAWordToTheCarsWidth() {
        val face = Face("t", Line("{a}"), Line("{b}"), Line("{c}"))
        val s = face.render(fields("a" to v("Maple Street Cafe Extra"), "b" to v("x"), "c" to v("y"))).sanitized()
        assertEquals("Maple Street", s.title)   // 15 wide: cut at a word, not "Maple Street Ca"
    }

    @Test fun faceMapsLinesToTheCarsRows() {
        val s = Face("t", Line("one"), Line("two"), Line("three")).render(fields())
        assertEquals("'one' / 'two' / 'three'", s.toString())   // title / album (line 2) / artist (line 3)
        assertEquals(CarProfile.current.safeTitle, s.titleMax)
    }

    @Test fun malformedPlaceholdersStayLiteral() {
        assertEquals("{}", Template.render("{}", fields()))
        assertEquals("{bar x}", Template.render("{bar x}", fields()))
        assertEquals("a { b", Template.render("a { b", fields()))
    }

    @Test fun reportsFieldsUsed() {
        assertEquals(setOf("clock", "speed", "song.progress"), Template.fieldsUsed("{clock}  {speed|--} km/h {bar song.progress 6}"))
    }
}
