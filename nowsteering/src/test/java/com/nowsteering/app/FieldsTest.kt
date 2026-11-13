package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FieldsTest {
    @Test fun catalogueMatchesFieldsDoc() {
        val doc = File("../docs/FIELDS.md").readText()
        for (f in FieldCatalog.all) assertTrue("docs/FIELDS.md lacks `${f.name}`", doc.contains("`${f.name}`"))
        // and the other way: every `name` in the first column of the table exists
        val inDoc = Regex("""^\| `([a-z_.<>]+)` \|""", RegexOption.MULTILINE).findAll(doc).map { it.groupValues[1] }.toSet()
        for (n in inDoc) assertTrue("FIELDS.md lists `$n`, which is not in the catalogue", n == "car.<pid>" || FieldCatalog.def(n) != null)
    }

    @Test fun namesAreShortDottedLowerCase() {
        for (f in FieldCatalog.all) assertTrue(f.name, Regex("""^[a-z][a-z_]*(\.[a-z_]+)*$""").matches(f.name))
    }

    @Test fun anyPidByIdIsACarField() {
        assertNotNull(FieldCatalog.def("car.0b"))
        assertNull(FieldCatalog.def("car."))
        assertNull(FieldCatalog.def("nonsense"))
        assertEquals(FieldCatalog.TORQUE_MAX_AGE_MS, FieldCatalog.maxAgeMs("car.ff1206"))
    }

    @Test fun anAgeAtTheLimitCountsAsMissing() {
        assertNull(Fields { FieldValue("30", 30.0, "km/h", 5000) }.get("speed"))
        assertNotNull(Fields { FieldValue("30", 30.0, "km/h", 4999) }.get("speed"))
    }

    @Test fun eachFieldIsReadOnceAndOnlyWhenAsked() {
        var reads = 0
        val f = Fields { reads++; FieldValue("x") }
        assertEquals(0, reads)
        f.get("clock"); f.get("clock"); f.text("clock")
        assertEquals(1, reads)
    }

    @Test fun torquePidIdsNormalise() {
        assertEquals("05", TorqueFields.normalize("5"))
        assertEquals("05", TorqueFields.normalize("0x05"))
        assertEquals("0c", TorqueFields.normalize("0C,Engine RPM"))
        assertEquals("ff1206", TorqueFields.normalize(" FF1206 "))
        assertEquals("42", TorqueFields.pidOf("car.voltage"))
        assertEquals("0b", TorqueFields.pidOf("car.0b"))
        assertNull(TorqueFields.pidOf("speed"))
    }

    @Test fun torqueFieldsReadFromASnapshot() {
        val coolant = TorquePid("05", "Engine Coolant Temperature", "Coolant", "°C", active = true, ecuSupported = true)
        val intake = TorquePid("0f", "Intake Air Temperature", "Intake", "°C", true, true)
        val snap = TorqueClient.Snapshot(obdConnected = true, bound = true, pids = listOf(coolant, intake),
            rows = mapOf("05" to (84.0 to 100_000L), "0f" to (41.0 to 100_000L)))
        val v = TorqueFields.read(snap, "car.coolant", 101_500)!!
        assertEquals("84", v.text); assertEquals("°C", v.unit); assertEquals(1500L, v.ageMs)
        assertEquals("41", TorqueFields.read(snap, "car.0f", 100_000)!!.text)        // by id, even though two PIDs are called "Intake"
        assertNull(TorqueFields.read(snap.copy(obdConnected = false), "car.coolant", 101_000))
        assertNull(TorqueFields.read(snap, "car.voltage", 101_000))                      // not in the list
        assertNull(TorqueFields.read(snap.copy(rows = mapOf("05" to (84.0 to 0L))), "car.coolant", 101_000))
        // a stale reading is dropped by Fields
        assertNull(Fields { TorqueFields.read(snap, it, 120_000) }.get("car.coolant"))
    }

    @Test fun fuelLevelIsNeverShown() {
        val f = Fields(CarProfile.current.neverShow) { FieldValue("2.75", 2.75) }
        assertNull(f.get("car.fuel_level"))
    }
}

class CarProfileTest {
    @Test fun gluesTheOldConstantsToTheProfile() {
        val p = CarProfile.HONDA_CITY_2014
        assertEquals(15, Glyphs.TITLE_MAX); assertEquals(p.safeTitle, Glyphs.TITLE_MAX)
        assertEquals(18, Glyphs.LINE_MAX); assertEquals(p.safeLine, Glyphs.LINE_MAX)
        assertEquals(20, Glyphs.SONG_MAX); assertEquals(p.songLine, Glyphs.SONG_MAX)
        assertEquals("←→↑↓↖↗•●▲▶▼█▓▒▄", p.confirmedGlyphs)
        assertEquals(1000L, DisplayEngine.MIN_INTERVAL_MS)
        assertEquals(2000L, ForwardGuard.SETTLE_MS)
    }

    @Test fun measuredWidthsAreTheHondasOwn() {
        val p = CarProfile.HONDA_CITY_2014
        assertEquals(listOf(17, 20, 20), listOf(p.measuredTitle, p.measuredLine2, p.measuredLine3))
        assertEquals(listOf("title", "album", "artist"), p.rowOrder)
    }

    @Test fun currentPresetNeverExceedsTheMeasuredWidths() {
        val p = CarProfile.current
        for (settings in listOf(true, false).flatMap { a -> listOf(true, false).map { b -> Settings(a, b, a, b, a, SongPrefix.ARROW) } }) {
            for (r in CurrentPreset.build(settings).rules) {
                assertTrue(r.id, r.face.line1.alts.all { (it.max ?: p.safeTitle) <= p.measuredTitle })
                assertTrue(r.id, r.face.line2.alts.all { (it.max ?: p.safeLine) <= p.measuredLine2 })
                assertTrue(r.id, r.face.line3.alts.all { (it.max ?: p.safeLine) <= p.measuredLine3 })
            }
        }
    }

    @Test fun currentPresetAvoidsGlyphsThatDrawAsDots() {
        val bad = "░▀⇐♪♫♩♬♭♯♠♣♥♦☼◊"
        for (settings in listOf(Settings(true, true, true, true, true, SongPrefix.ARROW), Settings(false, false, false, false, false, SongPrefix.NONE))) {
            for (r in CurrentPreset.build(settings).rules) for (l in listOf(r.face.line1, r.face.line2, r.face.line3)) for (a in l.alts)
                assertTrue("${r.id}: ${a.template}", a.template.none { it in bad })
        }
    }
}
