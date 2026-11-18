package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PresetsTest {
    private val base = Settings(keepOnScreen = true, idleSeconds = false, arrivalTime = false, tripBar = true, turnBar = true,
        songPrefix = SongPrefix.ARROW, preset = PresetId.DRIVER, fuelPrice = null, typicalKmpl = null)

    private fun lines(s: Screen) = "${s.title} | ${s.album} | ${s.artist}"
    private fun sit(preset: PresetId, name: String, settings: Settings = base) =
        PresetPreview.render(settings.copy(preset = preset)).first { it.name.startsWith(name) }.screen

    // ---- Driver: the PM's mock-ups ----
    @Test fun driverIdle() = assertEquals("17:03 42 km/h | Sat 14 Nov | Now Steering", lines(sit(PresetId.DRIVER, "Idle")))
    @Test fun driverMusic() = assertEquals("42 km/h 17:03 | ▶ Falling - Trevor | 2:31 left ██▒▒▒▒", lines(sit(PresetId.DRIVER, "Music")))
    @Test fun driverNavFarWithMusic() = assertEquals("↗ 2.4 km 42 km/h | ▶ Falling - Trevor | 2:31 left ██▒▒▒▒", lines(sit(PresetId.DRIVER, "Navigating, music")))
    @Test fun driverNavFarNoMusic() = assertEquals("↗ 2.4 km 42 km/h | 23 min ▒▒▒▒▒▒ | Outer Ring Rd", lines(sit(PresetId.DRIVER, "Navigating, no")))
    @Test fun driverNearTurn() = assertEquals("↗ 250 m | ↗ ██▒▒▒▒▒▒▒▒ | 42 km/h ▶ 2:31", lines(sit(PresetId.DRIVER, "Last 300")))
    @Test fun driverRerouting() = assertEquals("• Rerouting... |  | ", lines(sit(PresetId.DRIVER, "Rerouting")))
    @Test fun driverArrived() = assertEquals("● Arrived | 18:05 | Hosur Rd", lines(sit(PresetId.DRIVER, "Arrived")))

    // ---- Trip ----
    @Test fun tripNavFarShowsTheCost() {
        // price Rs 100/L sample: fuel 0.48 L = 48 now; 11.2 km at 14.6 km/L = 77 to go; total 125
        assertEquals("↗ 2.4 km | 23 min ▒▒▒▒▒▒ | Rs 125 trip · 48 now", lines(sit(PresetId.TRIP, "Navigating, music")))
    }

    @Test fun tripWithoutAPriceHidesTheCostLine() {
        val noPrice = PresetLibrary.build(base.copy(preset = PresetId.TRIP))
        val i = liveNav(base.copy(preset = PresetId.TRIP), price = null)
        assertEquals("Outer Ring Rd", noPrice.resolve(LiveContext(i, TurnZone())).sanitized().artist)
    }

    @Test fun tripCostUsesTildeWhenItFitsAndShortensWhenItDoesNot() {
        fun line3(price: Double, fuel: Double): String {
            val s = base.copy(preset = PresetId.TRIP, fuelPrice = price)
            val i = liveNav(s, price = price, fuel = fuel)
            return PresetLibrary.build(s).resolve(LiveContext(i, TurnZone())).sanitized().artist
        }
        // price 10, fuel 0.48 L: 5 now; 11.2 km at 14.6 km/L = 7.7 to go; total 12.5
        assertEquals("Rs ~12 trip · ~5 now", line3(price = 10.0, fuel = 0.48))
        // price 1000: 480 now + 767 to go = 1247: "Rs ~1247 trip · ~480 now" does not fit 20, nor without "~"; the total alone does
        assertEquals("Rs ~1247 trip", line3(price = 1000.0, fuel = 0.48))
    }

    @Test fun tripNearTurnAndMusicAreAsDriver() {
        assertEquals(lines(sit(PresetId.DRIVER, "Last 300")), lines(sit(PresetId.TRIP, "Last 300")))
        assertEquals(lines(sit(PresetId.DRIVER, "Idle")), lines(sit(PresetId.TRIP, "Idle")))
        assertEquals(lines(sit(PresetId.DRIVER, "Music")), lines(sit(PresetId.TRIP, "Music")))
    }

    // ---- Car ----
    @Test fun carIdleAndMusicShowCarData() {
        assertEquals("17:03 42 km/h | 84°C 14.2V 14.6km/L | Now Steering", lines(sit(PresetId.CAR, "Idle")))
        assertEquals("42 km/h 17:03 | ▶ Falling - Trevor | 84°C 14.2V 14.6km/L", lines(sit(PresetId.CAR, "Music")))
    }

    @Test fun carDropsKmplFirstThenVoltage() {
        fun idle(vararg car: Pair<String, FieldValue>): String {
            val m = car.toMap()
            val i = LiveInputs(base.copy(preset = PresetId.CAR), PresetPreview.sampleTime, 40, 0L, null, null, { null }, null, { null }, other = { m[it] })
            return PresetLibrary.build(base.copy(preset = PresetId.CAR)).resolve(LiveContext(i, TurnZone())).sanitized().album
        }
        val c = FieldValue("84", 84.0); val v = FieldValue("14.2", 14.2); val k = FieldValue("14.6", 14.6)
        assertEquals("84°C 14.2V 14.6km/L", idle("car.coolant" to c, "car.voltage" to v, "car.trip_kmpl" to k))
        assertEquals("84°C 14.2V", idle("car.coolant" to c, "car.voltage" to v))
        assertEquals("84°C", idle("car.coolant" to c))
        assertEquals("14.2V", idle("car.voltage" to v))
        assertEquals("Sat 14 Nov", idle())      // no car data at all: the date, never a dash
    }

    // ---- Classic ----
    @Test fun classicIsTheOldPreset() {
        val c = base.copy(preset = PresetId.CLASSIC)
        assertEquals(CurrentPreset.build(c).rules.map { it.id }, PresetLibrary.build(c).rules.map { it.id })
        assertEquals("17:03 42 km/h | Now Steering | Sat 14 Nov", lines(sit(PresetId.CLASSIC, "Idle")))
    }

    // ---- never wrong: missing means dropped ----
    @Test fun noDashesAnywhereWhenSpeedAndSongAreMissing() {
        for (id in PresetId.entries) {
            val s = base.copy(preset = id)
            val none = LiveInputs(s, PresetPreview.sampleTime, null, null, null, null, { null }, null, { null })
            val out = PresetLibrary.build(s).resolve(LiveContext(none, TurnZone())).sanitized()
            if (id != PresetId.CLASSIC) assertFalse("$id: ${lines(out)}", lines(out).contains("--"))
            assertFalse("$id: ${lines(out)}", lines(out).contains("{"))
        }
        val i = LiveInputs(base, PresetPreview.sampleTime, null, null, null, null, { null }, null, { null })
        assertEquals("17:03", PresetLibrary.build(base).resolve(LiveContext(i, TurnZone())).sanitized().title)
    }

    @Test fun staleSpeedIsDroppedNotShown() {
        val i = LiveInputs(base, PresetPreview.sampleTime, 88, 5_000L, null, null, { null }, null, { null })
        assertEquals("17:03", PresetLibrary.build(base).resolve(LiveContext(i, TurnZone())).sanitized().title)
    }

    // ---- widths ----
    @Test fun line1FallsBackWhenSpeedDoesNotFit() {
        fun title(distance: String, speed: Int): String {
            val nav = NavState.Info(NavText(distance, "Rd", "18:00", "20 km", null, "30 min"), "←", "t", null)
            val i = LiveInputs(base, PresetPreview.sampleTime, speed, 0L, null, nav, { null }, null, { null })
            return PresetLibrary.build(base).resolve(LiveContext(i, TurnZone())).sanitized().title
        }
        assertEquals("← 750 m 32 km/h", title("750 m", 32))
        assertEquals("← 12.5 km 120", title("12.5 km", 120))           // "← 12.5 km 120 km/h" is 18: drop " km/h"
        assertEquals("← 12.5 km 120", title("12.5 km", 120))
        assertEquals("← 1.2 km 99 km/h", title("1.2 km", 99))          // exactly 16: fits
        assertEquals("← 120 m 120 km/h", title("120 m", 120))          // 16
    }

    @Test fun everyFaceStaysInsideTheMeasuredWidths() {
        val p = CarProfile.current
        val r = Random(7)
        val long = listOf("WWWWWWWWWWWWWWWWWWWWWWWW", "Sri Venkateshwara Swamy Temple Rd", "Falling in Love with the Sound of Rain", "1:02:31", "12.5 km")
        for (id in PresetId.entries) for (keep in listOf(true, false)) for (tripBar in listOf(true, false)) {
            val s = base.copy(preset = id, keepOnScreen = keep, tripBar = tripBar, arrivalTime = !tripBar, fuelPrice = 1000.0, typicalKmpl = 12.0)
            repeat(200) {
                val road = long[r.nextInt(long.size)]
                val nav = if (r.nextBoolean()) NavState.Info(NavText("${r.nextInt(900)} m", road, "11:59pm", "${r.nextInt(99)} km", null, "1 hr 59 min"), "↗", "t", null) else null
                val song = if (r.nextBoolean()) SessionWatcher.Song("x", long[r.nextInt(long.size)], long[r.nextInt(long.size)], 4_000_000, 3_000_000) else null
                val i = LiveInputs(s, PresetPreview.sampleTime, r.nextInt(200), 0L, null, nav, { r.nextDouble() * 1500 }, 90_000.0, { song },
                    tripKm = r.nextInt(500).toDouble(), other = { mapOf("car.coolant" to FieldValue("104", 104.0), "car.voltage" to FieldValue("13.8", 13.8),
                        "car.trip_kmpl" to FieldValue("15.5", 15.5), "car.fuel_used" to FieldValue("40.00", 40.0))[it] })
                val out = PresetLibrary.build(s).resolve(LiveContext(i, TurnZone())).sanitized()
                assertTrue("$id title ${out.title}", out.title.length <= p.measuredTitle)
                assertTrue("$id line2 ${out.album}", out.album.length <= p.measuredLine2)
                assertTrue("$id line3 ${out.artist}", out.artist.length <= p.measuredLine3)
                assertTrue(lines(out), listOf(out.title, out.album, out.artist).none { it.contains('{') || it.contains('}') })
            }
        }
    }

    @Test fun faceTemplatesNeverAskForMoreThanTheCarCanShow() {
        val p = CarProfile.current
        for (id in PresetId.entries) for (settings in listOf(base, base.copy(arrivalTime = true, tripBar = false, songPrefix = SongPrefix.NONE))) {
            for (r in PresetLibrary.build(settings.copy(preset = id)).rules) {
                assertTrue("$id/${r.id}", r.face.line1.alts.all { (it.max ?: p.safeTitle) <= p.measuredTitle })
                assertTrue("$id/${r.id}", r.face.line2.alts.all { (it.max ?: p.safeLine) <= p.measuredLine2 })
                assertTrue("$id/${r.id}", r.face.line3.alts.all { (it.max ?: p.safeLine) <= p.measuredLine3 })
            }
        }
    }

    @Test fun nothingUsesGlyphsThatDrawAsDots() {
        val bad = "░▀⇐♪♫♩♬♭♯♠♣♥♦☼◊"
        for (id in PresetId.entries) for (r in PresetLibrary.build(base.copy(preset = id)).rules)
            for (l in listOf(r.face.line1, r.face.line2, r.face.line3)) for (a in l.alts) assertTrue("$id ${a.template}", a.template.none { it in bad })
    }

    // ---- rules and priorities ----
    @Test fun priorityStaysArrivedReroutingNearNavigatingMusicIdle() {
        for (id in listOf(PresetId.DRIVER, PresetId.TRIP, PresetId.CAR)) {
            val ids = PresetLibrary.build(base.copy(preset = id)).rules.map { it.id }.filter { it != "nav_music" }
            assertEquals(listOf("arrived", "rerouting", "near_turn", "navigating", "music_keep_on", "idle"), ids)
        }
    }

    @Test fun nearTurnUsesHysteresis300Enter340Leave() {
        val zone = TurnZone()
        fun at(m: Double): String {
            val i = LiveInputs(base, PresetPreview.sampleTime, 40, 0L, null, PresetPreview.render(base).let { navInfo() }, { m }, null, { null })
            return PresetLibrary.build(base).pick(LiveContext(i, zone)).id
        }
        assertEquals("navigating", at(320.0)); assertEquals("near_turn", at(299.0)); assertEquals("near_turn", at(330.0)); assertEquals("navigating", at(341.0))
    }

    @Test fun cyclingGoesDriverTripCarClassicAndSkipsCarWithoutTorque() {
        assertEquals(PresetId.TRIP, PresetId.next(PresetId.DRIVER, true))
        assertEquals(PresetId.CAR, PresetId.next(PresetId.TRIP, true))
        assertEquals(PresetId.CLASSIC, PresetId.next(PresetId.CAR, true))
        assertEquals(PresetId.DRIVER, PresetId.next(PresetId.CLASSIC, true))
        assertEquals(PresetId.CLASSIC, PresetId.next(PresetId.TRIP, false))
        assertEquals(listOf(PresetId.DRIVER, PresetId.TRIP, PresetId.CLASSIC), PresetId.available(false))
        assertEquals(PresetId.DRIVER, PresetId.of(null))
        assertEquals(PresetId.DRIVER, PresetId.of("nonsense"))
    }

    @Test fun previewShowsEveryPresetWithinTheCarsWidths() {
        for (id in PresetId.entries) for (sit in PresetPreview.render(base.copy(preset = id))) {
            assertTrue("$id ${sit.name}", sit.screen.title.length <= 17 && sit.screen.album.length <= 20 && sit.screen.artist.length <= 20)
        }
    }

    // ---- helpers ----
    private fun navInfo() = NavState.Info(NavText("750 m", "13th Main Rd", "07:04", "2.6 km", null, "7 min"), "←", "t", null)

    private fun liveNav(s: Settings, price: Double?, fuel: Double = 0.48): LiveInputs {
        val nav = NavState.Info(NavText("2.4 km", "Outer Ring Rd", "18:05", "11.2 km", null, "23 min"), "↗", "t", null)
        val car = mapOf("car.trip_kmpl" to FieldValue("14.6", 14.6), "car.fuel_used" to FieldValue("$fuel", fuel))
        return LiveInputs(s.copy(fuelPrice = price), PresetPreview.sampleTime, 42, 0L, null, nav, { 2400.0 }, 12_000.0, { null }, tripKm = 7.0, other = { car[it] })
    }
}
