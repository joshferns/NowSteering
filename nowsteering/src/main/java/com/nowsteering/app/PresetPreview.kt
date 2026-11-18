package com.nowsteering.app

import java.util.Date

/**
 * What a preset will show on the car in each situation, built with the real rules and templates and cut the way the car
 * cuts (widths 17 / 20 / 20, glyph fallbacks). Sample values stand in for live ones. Used by the main screen and the tests.
 */
object PresetPreview {
    data class Situation(val name: String, val screen: Screen, val note: String = "")

    private val sampleNav = NavState.Info(NavText("2.4 km", "Outer Ring Rd", "18:05", "11.2 km", null, "23 min"), "↗", "preview", null)
    private val sampleSong = SessionWatcher.Song("preview", "Falling", "Trevor Daniel", 240_000, 89_000)
    private val sampleCar: Map<String, FieldValue> = mapOf(
        "car.coolant" to FieldValue("84", 84.0, "°C"),
        "car.voltage" to FieldValue("14.2", 14.2, "V"),
        "car.rpm" to FieldValue("2100", 2100.0, "rpm"),
        "car.trip_kmpl" to FieldValue("14.6", 14.6, "km/l"),
        "car.kmpl_longterm" to FieldValue("13.9", 13.9, "km/l"),
        "car.fuel_used" to FieldValue("0.48", 0.48, "l"),
    )

    /** Fixed so the preview does not change every minute. */
    val sampleTime: Date = java.util.Calendar.getInstance().apply { set(2026, 10, 14, 17, 3, 0) }.time

    fun render(settings: Settings, speedKmh: Int = 42): List<Situation> {
        val price = settings.fuelPrice
        val s = settings.copy(keepOnScreen = true, idleSeconds = false, fuelPrice = price ?: 100.0)
        val preset = PresetLibrary.build(s)
        val note = if (price == null && s.preset == PresetId.TRIP) "sample price Rs 100/L" else ""

        fun screen(nav: NavState.Info?, song: SessionWatcher.Song?, toTurn: Double? = null, arrived: Arrived? = null): Screen {
            val inp = LiveInputs(s, sampleTime, speedKmh, 0L, arrived, nav, { toTurn }, 12_000.0, { song }, tripKm = 7.0,
                other = { sampleCar[it] })
            return preset.resolve(LiveContext(inp, TurnZone())).sanitized()
        }
        val idle = screen(null, null)
        val out = ArrayList<Situation>()
        out += Situation("Idle, nothing playing", idle)
        out += Situation("Music playing (Keep on screen)", screen(null, sampleSong))
        out += Situation("Navigating, music playing", screen(sampleNav, sampleSong, toTurn = 2400.0), note)
        out += Situation("Navigating, no music", screen(sampleNav, null, toTurn = 2400.0), note)
        out += Situation("Last 300 m before a turn", screen(sampleNav, sampleSong, toTurn = 250.0))
        out += Situation("Rerouting", screen(sampleNav.copy(text = sampleNav.text.copy(road = "Rerouting...")), null, toTurn = 900.0))
        out += Situation("Arrived", screen(null, null, arrived = Arrived("Hosur Rd", "18:05")))
        out += Situation("Coolant warning on idle", Warnings.apply(idle, WarnSlot.LINE1, Warn(WarnKind.HOT, 107.0)).sanitized())
        return out
    }

    /** The three lines, top to bottom as on the car (title / line 2 / line 3). */
    fun lines(s: Screen) = listOf(s.title, s.album, s.artist)

    fun format(list: List<Situation>): String = list.joinToString("\n\n") { sit ->
        val l = lines(sit.screen)
        val rows = l.mapIndexed { i, t -> "  ${i + 1}  ${t.ifEmpty { "·" }}" }
        "${sit.name}${if (sit.note.isNotEmpty()) " (${sit.note})" else ""}\n${rows.joinToString("\n")}"
    }
}
