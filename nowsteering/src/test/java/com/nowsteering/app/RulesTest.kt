package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class RulesTest {
    private val s0 = Settings(keepOnScreen = false, idleSeconds = false, arrivalTime = false, tripBar = true, turnBar = true, songPrefix = SongPrefix.ARROW)
    private fun nav(road: String = "13th Main Rd", dist: String = "750 m") =
        NavState.Info(NavText(dist, road, "07:04", "2.6 km", null, "7 min"), "←", "test", null)
    private val song = SessionWatcher.Song("x", "Falling", "Trevor Daniel", 200_000, 50_000)

    private fun inputs(settings: Settings = s0, nav: NavState.Info? = null, toTurn: Double? = null, arrived: Arrived? = null,
                       song: SessionWatcher.Song? = null) =
        LiveInputs(settings, Date(1_793_000_000_000L), 32, 0L, arrived, nav, { toTurn }, 3000.0, { song })

    private fun pick(i: LiveInputs, zone: TurnZone = TurnZone()) = CurrentPreset.build(i.settings).pick(LiveContext(i, zone)).id

    @Test fun idleWhenNothingElseApplies() = assertEquals("idle", pick(inputs()))

    @Test fun musicOnlyWithKeepOnAndASong() {
        assertEquals("idle", pick(inputs(song = song)))                                   // Keep on screen is OFF by default
        assertEquals("music_keep_on", pick(inputs(s0.copy(keepOnScreen = true), song = song)))
        assertEquals("idle", pick(inputs(s0.copy(keepOnScreen = true))))                    // nothing playing
        assertEquals("idle", pick(inputs(s0.copy(keepOnScreen = true), song = song.copy(title = "🚗"))))   // title unreadable
    }

    @Test fun navigatingBeatsMusic() = assertEquals("navigating", pick(inputs(s0.copy(keepOnScreen = true), nav = nav(), toTurn = 750.0, song = song)))

    @Test fun nearTurnBeatsNavigating() = assertEquals("near_turn_bar", pick(inputs(nav = nav(), toTurn = 250.0)))

    @Test fun nearTurnWithoutTheBarSetting() = assertEquals("near_turn", pick(inputs(s0.copy(turnBar = false), nav = nav(), toTurn = 250.0)))

    @Test fun reroutingBeatsNearAndNavigating() = assertEquals("rerouting", pick(inputs(nav = nav(road = "Rerouting..."), toTurn = 100.0)))

    @Test fun arrivedBeatsEverything() =
        assertEquals("arrived", pick(inputs(s0.copy(keepOnScreen = true), nav = nav(road = "Rerouting..."), toTurn = 10.0, arrived = Arrived("x", "19:40"), song = song)))

    @Test fun nearUsesTheHysteresis300Enter340Leave() {
        val zone = TurnZone()
        fun at(m: Double) = pick(inputs(nav = nav(), toTurn = m), zone)
        assertEquals("navigating", at(320.0))
        assertEquals("near_turn_bar", at(299.0))
        assertEquals("near_turn_bar", at(330.0))     // still near below 340
        assertEquals("navigating", at(341.0))
    }

    @Test fun theTurnZoneOnlyMovesWhenTheNavRulesAreReached() {
        val zone = TurnZone()
        pick(inputs(nav = nav(), toTurn = 100.0), zone)
        assertTrue(zone.near)
        pick(inputs(nav = nav(), toTurn = 1000.0, arrived = Arrived("x", "1")), zone)    // arrived: nav rules not reached
        assertTrue(zone.near)
        pick(inputs(nav = nav(road = "Rerouting..."), toTurn = 1000.0), zone)             // rerouting: stops before "near"
        assertTrue(zone.near)
    }

    @Test fun currentFacesMatchTheDocumentedMockups() {
        fun screen(i: LiveInputs) = CurrentPreset.build(i.settings).resolve(LiveContext(i, TurnZone())).sanitized().toString()
        val idle = CurrentPreset.build(s0).resolve(LiveContext(inputs(), TurnZone())).sanitized()
        assertEquals("Now Steering", idle.album)                                               // line 2
        assertEquals(java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.US).format(Date(1_793_000_000_000L)), idle.artist)   // line 3
        assertEquals("'← 750 m' / '7 min ███▒▒▒' / '13th Main Rd'", screen(inputs(nav = nav(), toTurn = 750.0)).replace(Regex("███?▒+|█+▒+"), "███▒▒▒"))
        assertEquals("'← 250 m' / '← ████▒▒▒▒▒▒' / '13th Main Rd'".replace("████▒▒▒▒▒▒", "██▒▒▒▒▒▒▒▒"), screen(inputs(nav = nav(), toTurn = 250.0)).replace(Regex("[█▒]{10}"), "██▒▒▒▒▒▒▒▒"))
        assertEquals("'● Arrived' / '19:40' / 'Main Rd'", screen(inputs(arrived = Arrived("Main Rd", "19:40"))))
        assertEquals("'• Rerouting...' / '' / ''", screen(inputs(nav = nav(road = "Rerouting..."))))
    }

    @Test fun missingSpeedShowsDashesAsBefore() {
        val i = LiveInputs(s0, Date(1_793_000_000_000L), null, null, null, null, { null }, null, { null })
        assertTrue(CurrentPreset.build(s0).resolve(LiveContext(i, TurnZone())).sanitized().title.endsWith("-- km/h"))
    }

    @Test fun staleSpeedShowsDashes() {
        val i = LiveInputs(s0, Date(1_793_000_000_000L), 40, 5_000L, null, null, { null }, null, { null })
        assertTrue(CurrentPreset.build(s0).resolve(LiveContext(i, TurnZone())).sanitized().title.endsWith("-- km/h"))
    }

    @Test fun warningLayerSitsAboveAndExpires() {
        val w = WarningLayer()
        assertNull(w.active(0))
        w.post(WarningLayer.POWER_SAVING, 6000, now = 1000)
        assertEquals("power_saving", w.active(6999)!!.id)
        assertNull(w.active(7000))
        w.post(WarningLayer.NAV_ENDED, 4000, now = 0); w.clear()
        assertNull(w.active(1))
    }

    @Test fun existingNoticesKeepTheirOldRows() {
        // v0.4.0: Screen("Power saving on", "nav less smooth", "turn it off") = title, artist (line 3), album (line 2).
        assertEquals("'Power saving on' / 'turn it off' / 'nav less smooth'", WarningLayer.POWER_SAVING.render(Fields()).toString())
        assertEquals("'No music app' / '' / 'Start music first'", WarningLayer.NO_MUSIC_APP.render(Fields()).toString())
        assertEquals("'No music link' / 'in Now Steering app' / 'Grant notif access'", WarningLayer.NO_MUSIC_LINK.render(Fields()).toString())
        assertEquals("'Nav ended' / '' / ''", WarningLayer.NAV_ENDED.render(Fields()).toString())
    }

    @Test fun nothingPutsCarDataOnAFaceYet() {
        // Torque fields are readable, but no face of the Current preset uses them.
        for (settings in listOf(s0, s0.copy(keepOnScreen = true, arrivalTime = true, tripBar = false, songPrefix = SongPrefix.NONE))) {
            val used = CurrentPreset.build(settings).rules.flatMap { r -> listOf(r.face.line1, r.face.line2, r.face.line3) }
                .flatMap { it.alts }.flatMap { Template.fieldsUsed(it.template) }
            assertFalse(used.any { it.startsWith("car.") || it == "phone.battery" })
        }
    }
}
