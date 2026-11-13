package com.nowsteering.app

/** What a rule's conditions are checked against. Flags are read lazily, so only the ones a rule list reaches are computed. */
interface RuleContext {
    val fields: Fields
    fun flag(name: String): Boolean
}

/** A condition: a named situation flag, or a field that must be present. */
sealed interface Cond {
    data class Flag(val name: String) : Cond
    data class Has(val field: String) : Cond
}

/** When all [all] hold, show [face]. */
data class Rule(val id: String, val all: List<Cond>, val face: Face)

/** An ordered rule list. The first rule whose conditions all hold wins; the last one has no conditions. */
data class Preset(val name: String, val rules: List<Rule>) {
    fun pick(ctx: RuleContext): Rule {
        for (r in rules) {
            val ok = r.all.all { c ->
                when (c) {
                    is Cond.Flag -> ctx.flag(c.name)
                    is Cond.Has -> ctx.fields.get(c.field) != null
                }
            }
            if (ok) return r
        }
        return rules.last()
    }

    fun resolve(ctx: RuleContext): Screen = pick(ctx).face.render(ctx.fields)
}

/** The settings the Current preset reads. */
data class Settings(
    val keepOnScreen: Boolean,
    val idleSeconds: Boolean,
    val arrivalTime: Boolean,
    val tripBar: Boolean,
    val turnBar: Boolean,
    val songPrefix: SongPrefix,
) {
    companion object {
        fun fromPrefs() = Settings(Prefs.keepOnScreen, Prefs.idleSeconds, Prefs.arrivalTime, Prefs.tripBar, Prefs.turnBar, Prefs.songPrefix)
    }
}

/**
 * The first preset, "Current": today's screens as data. Situations, highest first:
 * arrived, rerouting, near the turn (last ~300 m, enter 300 / leave 340), navigating, music playing with Keep on screen, idle.
 *
 * Faces are built from the settings each time (a few small objects), so a changed setting applies at once.
 */
object CurrentPreset {
    const val NAME = "Current"

    fun build(s: Settings, profile: CarProfile = CarProfile.current): Preset {
        val line = profile.safeLine
        val song = profile.songLine
        val title = if (s.idleSeconds) "{clock.seconds} {speed|--}km/h" else "{clock}  {speed|--} km/h"
        val extra = s.songPrefix.extra

        // Nav title: arrow + distance to the turn (either one alone if the other is missing).
        val navTitle = Line(listOf(Alt("{turn.arrow} {turn.distance}"), Alt("{turn.distance}"), Alt("{turn.arrow}")))

        // Far from the turn, line 2: Maps' time left plus the trip bar, or the arrival clock.
        val far = ArrayList<Alt>()
        if (s.arrivalTime) {
            far += Alt("{nav.time_left} · {nav.eta}", song, fitsIn = Glyphs.LINE_MAX)
            far += Alt("{nav.time_left} {nav.eta}", song)
            far += Alt("ETA {nav.eta}", song)
        }
        if (s.tripBar) {
            far += Alt("{nav.time_left} {bar nav.progress ${Bars.TRIP_CELLS}}", song)
            far += Alt("{nav.time_left}", song)
            far += Alt("{bar nav.progress ${Bars.TRIP_CELLS}}", song)
        } else {
            far += Alt("{nav.time_left}", song)
        }
        val farLine2 = Line(far)
        val road = Line(listOf(Alt("{turn.road}", line)))
        val prefix = if (s.songPrefix == SongPrefix.NONE) "" else "${s.songPrefix.glyph} "
        val songOrRoad = Line(listOf(Alt("$prefix{song.text}", song), Alt("{turn.road}", line)))
        val idleTitle = Line(title)

        val rules = listOf(
            Rule("arrived", listOf(Cond.Flag("arrived")),
                Face("arrived", Line("● Arrived"), Line("{arrived.time}"), Line("{arrived.road}"))),
            Rule("rerouting", listOf(Cond.Flag("nav.shown"), Cond.Flag("nav.rerouting")),
                Face("rerouting", Line("• Rerouting..."), Line.EMPTY, Line.EMPTY)),
            Rule("near_turn_bar", listOf(Cond.Flag("nav.shown"), Cond.Flag("near"), Cond.Flag("set.turn_bar")),
                Face("near_turn_bar", navTitle,
                    Line(listOf(Alt("{turn.arrow} {bar turn.progress ${Bars.TURN_CELLS}}", song), Alt("{bar turn.progress ${Bars.TURN_CELLS}}", song))),
                    road, extra)),
            Rule("near_turn", listOf(Cond.Flag("nav.shown"), Cond.Flag("near")),
                Face("near_turn", navTitle, farLine2, road, extra)),
            Rule("navigating", listOf(Cond.Flag("nav.shown")),
                Face("navigating", navTitle, farLine2, songOrRoad, extra)),
            Rule("music_keep_on", listOf(Cond.Flag("set.keep_on"), Cond.Has("song.title")),
                Face("music_keep_on", idleTitle, Line("▶ {song.title}", song),
                    Line(listOf(Alt("{song.time_left} left {bar song.progress ${Bars.TRIP_CELLS}}", song), Alt("{song.artist}", song))))),
            Rule("idle", emptyList(),
                Face("idle", idleTitle, Line("Now Steering"), Line("{date}"))),
        )
        return Preset(NAME, rules)
    }
}

/** The warning layer: a message that sits above every face until it runs out (Phase 1: the notices that already existed). */
class WarningLayer {
    private var face: Face? = null
    private var until = 0L

    fun post(f: Face, ms: Long, now: Long) { face = f; until = now + ms }
    fun clear() { face = null }
    fun active(now: Long): Face? = face?.takeIf { now < until }

    companion object {
        // Screen("a", "b", "c") in the old code was (title, artist = line 3, album = line 2).
        val POWER_SAVING = Face.literal("power_saving", "Power saving on", line2 = "turn it off", line3 = "nav less smooth")
        val NAV_ENDED = Face.literal("nav_ended", "Nav ended")
        val NO_MUSIC_APP = Face.literal("no_music_app", "No music app", line3 = "Start music first")
        val NO_MUSIC_LINK = Face.literal("no_music_link", "No music link", line2 = "in Now Steering app", line3 = "Grant notif access")
    }
}
