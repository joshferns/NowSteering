package com.nowsteering.app

import kotlin.math.roundToInt

/**
 * Distance to the next turn, shown on the car. Shows Maps' value while Maps is updating; counts
 * down with GPS speed only after Maps has been quiet for [GAP_MS] (e.g. phone locked).
 * When Maps comes back higher than what we show:
 *  - a single blip under [BLIP_M] is ignored,
 *  - higher on 2+ updates in a row: our countdown pauses until Maps catches up,
 *  - [BLIP_M] or more: jump to Maps.
 * So we never sit below Maps for more than a moment. Pure Kotlin with an injected clock, for tests.
 */
class DeadReckoner(private val now: () -> Long) {
    companion object {
        const val GAP_MS = 2000L
        const val HOLD_MAX_MS = 3000L
        const val MAX_MS = 90_000L
        const val BLIP_M = 30.0
    }

    private var instruction: String? = null
    private var mapsText: String? = null
    private var lastMapsAt = 0L
    private var base: Double? = null
    private var baseAt = 0L
    private var travelled = 0.0
    private var hold = false
    private var upStreak = 0

    fun reset() {
        instruction = null; mapsText = null; base = null; hold = false; upStreak = 0; travelled = 0.0
    }

    /** A Maps update. Returns a log line when it changed what we do, else null. */
    fun onMaps(instructionId: String, text: String, meters: Double?): String? {
        if (instructionId != instruction || meters == null || base == null) {
            instruction = instructionId; mapsText = text; lastMapsAt = now()
            rebase(meters); hold = false; upStreak = 0
            return null
        }
        if (text == mapsText) return null // Maps repeating itself (stale)
        mapsText = text
        lastMapsAt = now()
        val shown = shown()!!
        val diff = meters - shown
        return when {
            diff <= 0.5 -> {
                val was = if (travelled > 0.5 || hold) " (we showed ${fmt(shown)}, error ${(-diff).roundToInt()} m)" else null
                rebase(meters); hold = false; upStreak = 0
                was?.let { "Maps $text$it" }
            }
            diff >= BLIP_M -> {
                rebase(meters); hold = false; upStreak = 0
                "Maps $text is ${diff.roundToInt()} m above ${fmt(shown)}: jumped to Maps"
            }
            upStreak == 0 -> {
                upStreak = 1; rebase(shown)
                "Maps $text is ${diff.roundToInt()} m above ${fmt(shown)}: single blip ignored"
            }
            else -> {
                upStreak++; rebase(shown); hold = true
                "Maps $text above ${fmt(shown)} ${upStreak}x in a row: countdown paused until Maps catches up"
            }
        }
    }

    /** GPS speed over the last [dtMs]; only counts while Maps is quiet and we're not paused. */
    fun onSpeed(mps: Double, dtMs: Long) {
        if (base == null) return
        if (hold && now() - lastMapsAt > HOLD_MAX_MS) hold = false // Maps went quiet while paused: carry on
        if (hold || now() - lastMapsAt < GAP_MS) return
        travelled += mps * dtMs / 1000.0
    }

    /** Meters to show, or null when there's nothing to estimate (no metric distance from Maps). */
    fun shown(): Double? {
        val b = base ?: return null
        if (now() - baseAt > MAX_MS) return b
        return (b - travelled).coerceAtLeast(0.0)
    }

    private fun rebase(m: Double?) { base = m; baseAt = now(); travelled = 0.0 }
    private fun fmt(m: Double) = NavTextParser.formatMeters(m)
}
