package com.nowsteering.app

/** The presets a user can pick. Driver is the default. */
enum class PresetId(val label: String, val blurb: String) {
    DRIVER("Driver", "Speed always visible; music gets two lines and gives them up near a turn"),
    TRIP("Trip", "Navigation with what the trip costs; music and idle as Driver"),
    CAR("Car", "As Driver, with coolant, voltage and trip km/L from Torque Pro"),
    CLASSIC("Classic", "Exactly the screens of 0.5.0");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DRIVER

        /** Car needs Torque Pro installed; the rest are always offered. */
        fun available(torqueInstalled: Boolean) = entries.filter { it != CAR || torqueInstalled }

        /** The next preset in the list the user can pick from (double-tap Next). */
        fun next(current: PresetId, torqueInstalled: Boolean): PresetId {
            val list = available(torqueInstalled)
            val i = list.indexOf(current)
            return list[(i + 1) % list.size]
        }
    }
}

/** Builds the rule list of a preset from the settings. Everything on screen is data here: templates, widths, rule order. */
object PresetLibrary {
    fun build(s: Settings, profile: CarProfile = CarProfile.current): Preset = when (s.preset) {
        PresetId.CLASSIC -> CurrentPreset.build(s, profile)
        PresetId.DRIVER -> modern(s, PresetId.DRIVER, profile)
        PresetId.TRIP -> modern(s, PresetId.TRIP, profile)
        PresetId.CAR -> modern(s, PresetId.CAR, profile)
    }

    /** Driver, Trip and Car share the rules and most faces; they differ in a few lines. */
    private fun modern(s: Settings, v: PresetId, p: CarProfile): Preset {
        val t = p.measuredTitle       // 17: line 1 may use the full measured width in these presets
        val w = p.measuredLine3       // 20
        val clock = if (s.idleSeconds) "clock.seconds" else "clock"
        val arrowDistance = "{turn.arrow|} {turn.distance}"

        // Line 1 near a turn, and far from it in Trip: arrow + distance only.
        val turnTitle = Line(listOf(Alt(arrowDistance, t), Alt("{turn.distance}", t), Alt("{turn.arrow}", t)))
        // Driver / Car far from a turn: add the speed if it fits (then without "km/h", then not at all).
        val farTitle = if (v == PresetId.TRIP) turnTitle else Line(listOf(
            Alt("$arrowDistance  {speed} km/h", t, fitsIn = t),
            Alt("$arrowDistance  {speed}", t, fitsIn = t),
            Alt(arrowDistance, t), Alt("{turn.distance}", t), Alt("{turn.arrow}", t)))

        val timeAndBar = listOf(
            Alt("{nav.time_left} {bar nav.progress ${Bars.TRIP_CELLS}}", w),
            Alt("{nav.time_left}", w),
            Alt("{bar nav.progress ${Bars.TRIP_CELLS}}", w))
        val road = Alt("{turn.road}", w)
        val songLine = Line("▶ {song.text}", w)
        val songTime = Alt("{song.time_left} left {bar song.progress ${Bars.TRIP_CELLS}}", w)

        // Trip: what the trip costs, as long as it fits; the shortest form is the total. "~" says it is an estimate.
        val costLine = Line(listOf(
            Alt("Rs ~{trip.cost_total} trip · ~{trip.cost} now", w, fitsIn = w),
            Alt("Rs {trip.cost_total} trip · {trip.cost} now", w, fitsIn = w),
            Alt("Rs ~{trip.cost_total} trip", w, fitsIn = w),
            Alt("Rs {trip.cost_total} trip", w),
            road))

        // Car: coolant, voltage and trip km/L; km/L is dropped first, then voltage.
        val carAlts = listOf(
            Alt("{car.coolant}°C {car.voltage}V {car.trip_kmpl}km/L", w, fitsIn = w),
            Alt("{car.coolant}°C {car.voltage}V", w),
            Alt("{car.coolant}°C", w),
            Alt("{car.voltage}V", w))

        val arrived = Rule("arrived", listOf(Cond.Flag("arrived")),
            Face("arrived", Line("● Arrived"), Line("{arrived.time}"), Line("{arrived.road}")))
        val rerouting = Rule("rerouting", listOf(Cond.Flag("nav.shown"), Cond.Flag("nav.rerouting")),
            Face("rerouting", Line("• Rerouting..."), Line.EMPTY, Line.EMPTY))
        // The last ~300 m: the turn bar, the speed and the song's time left (music gives up its two lines).
        val near = Rule("near_turn", listOf(Cond.Flag("nav.shown"), Cond.Flag("near")),
            Face("near_turn", turnTitle,
                Line(listOf(Alt("{turn.arrow|} {bar turn.progress ${Bars.TURN_CELLS}}", w), Alt("{bar turn.progress ${Bars.TURN_CELLS}}", w))),
                Line(listOf(Alt("{speed} km/h  ▶ {song.time_left}", w), road))))
        val navMusic = Rule("nav_music", listOf(Cond.Flag("nav.shown"), Cond.Has("song.text")),
            Face("nav_music", farTitle, songLine, Line(listOf(songTime, road))), WarnSlot.LINE2)
        val nav = Rule("navigating", listOf(Cond.Flag("nav.shown")),
            Face("navigating", farTitle, Line(timeAndBar), if (v == PresetId.TRIP) costLine else Line(listOf(road))), WarnSlot.LINE2)
        val music = Rule("music_keep_on", listOf(Cond.Flag("set.keep_on"), Cond.Has("song.text")),
            Face("music_keep_on", Line("{speed} km/h  {$clock}", t), songLine,
                Line(if (v == PresetId.CAR) carAlts + songTime + Alt("{date}", w) else listOf(songTime, Alt("{date}", w)))), WarnSlot.LINE1)
        val idle = Rule("idle", emptyList(),
            Face("idle", Line("{$clock}  {speed} km/h", t),
                if (v == PresetId.CAR) Line(carAlts + Alt("{date}", w)) else Line("{date}"),
                Line("Now Steering")), WarnSlot.LINE1)

        val rules = buildList {
            add(arrived); add(rerouting); add(near)
            if (v != PresetId.TRIP) add(navMusic)
            add(nav); add(music); add(idle)
        }
        return Preset(v.label, rules)
    }
}
