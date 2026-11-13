package com.nowsteering.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The v0.4.0 screen code, copied unchanged from SteeringService (navScreen, idleScreen, mirrorScreen and the
 * refreshScreen priority), with its inputs passed in instead of read from the service. It is the reference
 * the v2 pipeline (rules + templates + fields) is compared with in [GoldenTest]. It uses the same unchanged helpers
 * the old code did: Layouts.songText / farLine2 / mirrorLine3, Bars, Glyphs, TurnZone.
 * Delete it together with those helpers once v2 has been in the car for a while.
 */
class LegacyScreens(val zone: TurnZone = TurnZone()) {
    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.US)
    private val secFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Same priority as v0.4.0's refreshScreen (without the notice and test overrides, which are not part of the faces). */
    fun screen(i: LiveInputs, mode: String = "AUTO"): Screen {
        val nav = i.nav
        return when {
            i.arrived != null -> Screen("● Arrived", i.arrived.road, i.arrived.time)
            nav != null && mode == "AUTO" -> navScreen(nav, i)
            else -> idleScreen(i)
        }
    }

    private fun navScreen(info: NavState.Info, i: LiveInputs): Screen {
        val t = info.text
        if (NavTextParser.isRerouting(t.road)) return Screen("• Rerouting...", "", "")
        val toTurn = i.toTurnMeters()
        val distance = toTurn?.let { NavTextParser.formatMeters(it) } ?: t.distance
        val title = "${info.glyph} $distance".trim()
        val near = zone.update(toTurn)
        val prefix = i.settings.songPrefix

        val line2 = if (near && i.settings.turnBar) {
            "${info.glyph} ${Bars.turnBar(minOf(toTurn!!, Bars.TURN_BAR_FROM_M))}"
        } else {
            Layouts.farLine2(t.timeLeft, t.remaining, t.eta, Bars.tripFraction(NavTextParser.metersOf(t.remaining), i.tripMeters),
                showArrival = i.settings.arrivalTime, showBar = i.settings.tripBar)
        }
        val song = if (near) null else i.song()?.let { Layouts.songText(it.title, it.artist, prefix) }
        return Screen.lines(title, line2, song ?: t.road,
            line2Max = 20, line3Max = if (song != null) Glyphs.SONG_MAX else Glyphs.LINE_MAX, extra = prefix.extra)
    }

    private fun idleScreen(i: LiveInputs): Screen {
        val now = i.now
        val fresh = i.speedKmh != null && i.speedAgeMs != null && i.speedAgeMs < 5000
        val speed = if (fresh) "${i.speedKmh}" else "--"
        val title = if (i.settings.idleSeconds) "${secFmt.format(now)} ${speed}km/h" else "${clockFmt.format(now)}  $speed km/h"
        if (i.settings.keepOnScreen) mirrorScreen(title, i)?.let { return it }
        return Screen(title, dateFmt.format(now), "Now Steering")
    }

    private fun mirrorScreen(title: String, i: LiveInputs): Screen? {
        val song = i.song() ?: return null
        if (Glyphs.sanitize(song.title, 40).count { it.isLetterOrDigit() } < 2) return null
        val artist = Glyphs.sanitize(song.artist, 40)
        return Screen.lines(title, "▶ ${song.title}", Layouts.mirrorLine3(song.durationMs, song.positionMs, artist),
            line2Max = Glyphs.SONG_MAX, line3Max = Glyphs.SONG_MAX)
    }
}
