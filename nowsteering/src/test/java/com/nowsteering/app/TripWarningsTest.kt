package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMathTest {
    private fun inputs(price: Double? = 100.0, typical: Double? = null, km: Double? = 5.0, fuel: Double? = 0.5, trip: Double? = 15.0,
                       long: Double? = 12.0, left: Double? = 30.0) = TripInputs(price, typical, km, fuel, trip, long, left)

    @Test fun costIsFuelUsedTimesPrice() = assertEquals(50.0, TripMath.compute(inputs()).cost!!, 1e-9)

    @Test fun kmplIsTheTripAverageOnceTwoKmAreDriven() {
        val c = TripMath.compute(inputs(km = 2.0))
        assertEquals(15.0, c.kmpl!!, 1e-9); assertEquals("trip", c.kmplSource)
    }

    @Test fun beforeTwoKmItIsTheLongTermAverage() {
        val c = TripMath.compute(inputs(km = 1.99))
        assertEquals(12.0, c.kmpl!!, 1e-9); assertEquals("long-term", c.kmplSource)
    }

    @Test fun withoutTorqueItIsTheUsersTypicalKmpl() {
        val c = TripMath.compute(inputs(fuel = null, trip = null, long = null, typical = 11.0))
        assertEquals(11.0, c.kmpl!!, 1e-9); assertEquals("typical", c.kmplSource)
        // no fuel reading: cost so far comes from distance / typical km/L
        assertEquals(5.0 / 11.0 * 100.0, c.cost!!, 1e-9)
        assertEquals("distance / typical km/L x price", c.costSource)
    }

    @Test fun costToGoTotalAndPer10Km() {
        val c = TripMath.compute(inputs(km = 5.0, left = 30.0))      // km/L = long-term 12 (under 2 km? no: 5 km, trip 15)
        assertEquals(30.0 / 15.0 * 100.0, c.costToGo!!, 1e-9)         // 200
        assertEquals(50.0 + 200.0, c.total!!, 1e-9)
        assertEquals(10.0 / 15.0 * 100.0, c.per10Km!!, 1e-9)
    }

    @Test fun noPriceMeansEveryCostIsMissing() {
        for (p in listOf(null, 0.0, -3.0)) {
            val c = TripMath.compute(inputs(price = p))
            assertNull(c.cost); assertNull(c.costToGo); assertNull(c.total); assertNull(c.per10Km)
        }
    }

    @Test fun noNavigationMeansNoCostToGoAndNoTotal() {
        val c = TripMath.compute(inputs(left = null))
        assertNotNull(c.cost); assertNull(c.costToGo); assertNull(c.total); assertNotNull(c.per10Km)
    }

    @Test fun nothingKnownAboutFuelEconomyMeansNoToGo() {
        val c = TripMath.compute(inputs(trip = null, long = null, typical = null))
        assertNull(c.kmpl); assertNull(c.costToGo); assertNull(c.per10Km); assertNotNull(c.cost)
    }

    @Test fun absurdKmplReadingsAreIgnored() {
        val c = TripMath.compute(inputs(trip = 0.0, long = 500.0, typical = 10.0))
        assertEquals("typical", c.kmplSource)
    }

    @Test fun rupeesAreWholeNumbers() {
        assertEquals("125", TripMath.rupees(124.5)); assertEquals("124", TripMath.rupees(124.49)); assertEquals("0", TripMath.rupees(0.2))
    }

    @Test fun staleTorqueFuelMakesTheCostFieldMissing() {
        val s = Settings(true, false, false, true, true, SongPrefix.ARROW, PresetId.TRIP, 100.0, null)
        fun total(fuelAge: Long): String? {
            val nav = NavState.Info(NavText("2.4 km", "Rd", "18:05", "11.2 km", null, "23 min"), "↗", "t", null)
            val i = LiveInputs(s, PresetPreview.sampleTime, 40, 0L, null, nav, { 2400.0 }, 12_000.0, { null }, tripKm = 7.0,
                other = { mapOf("car.trip_kmpl" to FieldValue("14.6", 14.6), "car.fuel_used" to FieldValue("0.48", 0.48, "l", fuelAge))[it] })
            return LiveContext(i, TurnZone()).fields.text("trip.cost_total")
        }
        assertEquals("125", total(0)); assertNull(total(10_000))
    }

    @Test fun tripTrackerCountsMetersOnlyWhileRunning() {
        val t = TripTracker({ 0L }) { }
        t.addMeters(100.0)
        assertEquals(0.0, t.distanceKm, 0.0)
        t.start("test"); t.addMeters(1500.0); t.addMeters(500.0)
        assertEquals(2.0, t.distanceKm, 1e-9)
        t.end("test")
        assertTrue(!t.active)
    }
}

class WarningsTest {
    private val th = WarningThresholds()
    private fun steps(m: WarningMonitor, from: Long, seconds: Int, coolant: Double? = null, voltage: Double? = null, rpm: Double? = 800.0): List<Warn> {
        var out = emptyList<Warn>()
        for (s in 0..seconds) out = m.update(from + s * 1000L, coolant, voltage, rpm)
        return out
    }

    @Test fun coolantWarnsAfterFiveSecondsAbove105() {
        val m = WarningMonitor()
        assertTrue(steps(m, 0, 4, coolant = 106.0).isEmpty())
        assertEquals(listOf(Warn(WarnKind.HOT, 106.0)), steps(WarningMonitor(), 0, 5, coolant = 106.0))
    }

    @Test fun exactly105DoesNotWarn() = assertTrue(steps(WarningMonitor(), 0, 30, coolant = 105.0).isEmpty())

    @Test fun aBlipAbove105ResetsTheTimer() {
        val m = WarningMonitor()
        steps(m, 0, 3, coolant = 106.0)
        m.update(4000, 100.0, null, 800.0)
        assertTrue(steps(m, 5000, 4, coolant = 106.0).isEmpty())
    }

    @Test fun coolantClearsBelow102ForFiveSecondsAndStaysBetween() {
        val m = WarningMonitor()
        steps(m, 0, 6, coolant = 107.0)
        assertEquals(1, steps(m, 7000, 20, coolant = 103.0).size)          // between 102 and 105: still active
        assertEquals(1, steps(m, 30_000, 4, coolant = 101.0).size)         // 4 s below 102: still active
        assertTrue(steps(m, 40_000, 5, coolant = 101.0).isEmpty())         // 5 s: cleared
    }

    @Test fun coolantClearTimerResetsIfItRisesAgain() {
        val m = WarningMonitor()
        steps(m, 0, 6, coolant = 107.0)
        steps(m, 10_000, 3, coolant = 101.0)
        m.update(14_000, 103.0, null, 800.0)
        assertEquals(1, steps(m, 15_000, 4, coolant = 101.0).size)
    }

    @Test fun staleOrMissingReadingClearsAtOnce() {
        val m = WarningMonitor()
        assertEquals(1, steps(m, 0, 6, coolant = 108.0).size)
        assertTrue(m.update(7000, null, null, null).isEmpty())
    }

    @Test fun voltageWarnsBelow12_2ForTenSecondsWithTheEngineRunning() {
        assertTrue(steps(WarningMonitor(), 0, 9, voltage = 12.0).isEmpty())
        assertEquals(listOf(Warn(WarnKind.LOW, 12.0)), steps(WarningMonitor(), 0, 10, voltage = 12.0))
    }

    @Test fun lowVoltageWithTheEngineOffNeverWarns() {
        assertTrue(steps(WarningMonitor(), 0, 60, voltage = 11.5, rpm = 0.0).isEmpty())
        assertTrue(steps(WarningMonitor(), 0, 60, voltage = 11.5, rpm = 500.0).isEmpty())     // not above 500
        assertTrue(steps(WarningMonitor(), 0, 60, voltage = 11.5, rpm = null).isEmpty())
    }

    @Test fun voltageClearsAbove12_6ForFiveSeconds() {
        val m = WarningMonitor()
        steps(m, 0, 10, voltage = 12.0)
        assertEquals(1, steps(m, 11_000, 20, voltage = 12.4).size)         // between: still active
        assertEquals(1, steps(m, 40_000, 4, voltage = 12.7).size)
        assertTrue(steps(m, 50_000, 5, voltage = 12.7).isEmpty())
    }

    @Test fun engineStoppingClearsTheVoltageWarningAtOnce() {
        val m = WarningMonitor()
        assertEquals(1, steps(m, 0, 10, voltage = 12.0).size)
        assertTrue(m.update(11_000, null, 11.9, 0.0).isEmpty())
    }

    @Test fun theTextIsWhatTheCarShows() {
        assertEquals("▲ HOT 107°C", Warn(WarnKind.HOT, 106.6).text())
        assertEquals("▲ LOW 12.0V", Warn(WarnKind.LOW, 12.04).text())
        assertTrue(Warn(WarnKind.HOT, 120.0).text().length <= 17)
    }

    // ---- presenter: rhythm, worsening, both, placement ----
    private fun run(p: WarningPresenter, from: Long, seconds: Int, w: List<Warn>, canShow: Boolean = true) =
        (0..seconds).map { p.update(from + it * 1000L, w, canShow) }

    private val hot = listOf(Warn(WarnKind.HOT, 107.0))

    @Test fun shownAtOnceThenTenSecondsOnFiftyOff() {
        val p = WarningPresenter()
        val r = run(p, 1000, 130, hot)
        assertNotNull(r[0])                                               // at once
        assertNotNull(r[9]); assertNull(r[11])                            // 10 s on
        assertNull(r[40]); assertNull(r[59])                              // 50 s off
        assertNotNull(r[60])                                              // on again
        assertNull(r[75]); assertNotNull(r[122])
    }

    @Test fun worseByThreeDegreesShowsAgainAtOnce() {
        val p = WarningPresenter()
        run(p, 0, 15, hot)                                               // shown then hidden
        assertNull(p.update(16_000, listOf(Warn(WarnKind.HOT, 109.0)), true))   // +2: not enough
        assertNotNull(p.update(17_000, listOf(Warn(WarnKind.HOT, 110.0)), true)) // +3: now
    }

    @Test fun worseByPointThreeVoltsShowsAgain() {
        val p = WarningPresenter()
        val low = Warn(WarnKind.LOW, 12.0)
        run(p, 0, 15, listOf(low))
        assertNull(p.update(16_000, listOf(Warn(WarnKind.LOW, 11.8)), true))
        assertNotNull(p.update(17_000, listOf(Warn(WarnKind.LOW, 11.7)), true))
    }

    @Test fun bothWarningsAlternate() {
        val p = WarningPresenter()
        val both = listOf(Warn(WarnKind.HOT, 107.0), Warn(WarnKind.LOW, 12.0))
        val r = run(p, 0, 25, both)
        assertEquals(WarnKind.HOT, r[0]!!.kind)
        assertEquals(WarnKind.LOW, r[11]!!.kind)
        assertNull(r[22])
    }

    @Test fun heldDuringTheNearTurnPhaseAndShownRightAfter() {
        val p = WarningPresenter()
        assertNotNull(p.update(0, hot, true))
        for (s in 1..4) assertNotNull(p.update(s * 1000L, hot, true))      // 5 s of its 10 s used
        for (s in 5..40) assertNull(p.update(s * 1000L, hot, false))        // near turn: nothing shown, on-time kept
        val after = (41..60).map { p.update(it * 1000L, hot, true) }
        assertNotNull(after[0])                                            // right after the turn
        assertNotNull(after[4]); assertNull(after[7])                      // only the remaining ~5 s
    }

    @Test fun aWarningDueDuringTheTurnIsShownRightAfter() {
        val p = WarningPresenter()
        for (s in 0..20) assertNull(p.update(s * 1000L, hot, false))
        assertNotNull(p.update(21_000, hot, true))
    }

    @Test fun clearsAtOnceAndForgetsEverything() {
        val p = WarningPresenter()
        run(p, 0, 5, hot)
        assertNull(p.update(6_000, emptyList(), true))
        assertNotNull(p.update(7_000, hot, true))                           // a new warning shows at once, even inside the old off period
    }

    @Test fun theWarningReplacesTheRightLine() {
        val s = Screen.lines("17:03 42 km/h", "Now Steering", "Sat 14 Nov")
        val w = Warn(WarnKind.HOT, 107.0)
        assertEquals("▲ HOT 107°C", Warnings.apply(s, WarnSlot.LINE1, w).title)
        assertEquals("Now Steering", Warnings.apply(s, WarnSlot.LINE1, w).album)
        assertEquals("▲ HOT 107°C", Warnings.apply(s, WarnSlot.LINE2, w).album)
        assertEquals("17:03 42 km/h", Warnings.apply(s, WarnSlot.LINE2, w).title)
        assertEquals(s, Warnings.apply(s, WarnSlot.NONE, w))
    }

    @Test fun faceSlotsAreIdleAndMusicTopLineNavLine2NothingNearATurn() {
        for (id in listOf(PresetId.DRIVER, PresetId.TRIP, PresetId.CAR, PresetId.CLASSIC)) {
            val rules = PresetLibrary.build(Settings(true, false, false, true, true, SongPrefix.ARROW, id)).rules.associate { it.id to it.slot }
            assertEquals(WarnSlot.LINE1, rules["idle"]); assertEquals(WarnSlot.LINE1, rules["music_keep_on"])
            assertEquals(WarnSlot.LINE2, rules["navigating"])
            for (n in listOf("near_turn", "near_turn_bar", "arrived", "rerouting")) if (n in rules) assertEquals("$id $n", WarnSlot.NONE, rules[n])
        }
    }

    @Test fun thresholdsAreInTheCarProfile() {
        val w = CarProfile.HONDA_CITY_2014.warnings
        assertEquals(105.0, w.coolantHighC, 0.0); assertEquals(102.0, w.coolantClearC, 0.0)
        assertEquals(12.2, w.voltageLowV, 0.0); assertEquals(12.6, w.voltageClearV, 0.0)
        assertEquals(500.0, w.rpmMin, 0.0)
        assertTrue(w.describe().contains("105"))
    }
}

class SongTextTest {
    @Test fun unknownArtistsAreMissing() {
        for (a in listOf(null, "", "  ", "Unknown", "unknown", "<unknown>", " <Unknown> ", "Unknown Artist")) assertEquals(a, "", SongText.cleanArtist(a))
        assertEquals("Trevor Daniel", SongText.cleanArtist(" Trevor Daniel "))
        assertEquals("Unknown Mortal Orchestra", SongText.cleanArtist("Unknown Mortal Orchestra"))
    }

    @Test fun songLineDropsTheDashWhenTheArtistIsUnknown() {
        assertEquals("Falling", LiveContext.songBody("Falling", SongText.cleanArtist("<unknown>"), ""))
        assertEquals("Falling - Trevor Daniel", LiveContext.songBody("Falling", SongText.cleanArtist("Trevor Daniel"), ""))
    }
}
