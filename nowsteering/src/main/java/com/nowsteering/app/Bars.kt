package com.nowsteering.app

import kotlin.math.roundToInt

/** Progress bars from the two block glyphs the Honda renders: █ (done) and ▒ (to go). */
object Bars {
    const val TURN_CELLS = 10
    const val TRIP_CELLS = 6
    const val TURN_BAR_FROM_M = 300.0

    fun bar(fraction: Double, cells: Int): String {
        val done = (fraction.coerceIn(0.0, 1.0) * cells).roundToInt()
        return "█".repeat(done) + "▒".repeat(cells - done)
    }

    /** Fills over the last 300 m before a turn; null when the turn is further away. */
    fun turnBar(metersToTurn: Double?): String? {
        if (metersToTurn == null || metersToTurn > TURN_BAR_FROM_M) return null
        return bar(1 - metersToTurn / TURN_BAR_FROM_M, TURN_CELLS)
    }

    /** Trip progress 0..1 from distance left vs. the longest distance left seen this trip. */
    fun tripFraction(metersLeft: Double?, tripMeters: Double?): Double? {
        if (metersLeft == null || tripMeters == null || tripMeters <= 0) return null
        return (1 - metersLeft / tripMeters).coerceIn(0.0, 1.0)
    }

    fun tripBar(metersLeft: Double?, tripMeters: Double?): String? =
        tripFraction(metersLeft, tripMeters)?.let { bar(it, TRIP_CELLS) }
}
