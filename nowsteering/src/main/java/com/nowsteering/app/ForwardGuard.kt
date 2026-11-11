package com.nowsteering.app

import android.media.session.PlaybackState

/**
 * Decides whether a Next / Previous press may be forwarded to a music app right now.
 *
 * Forwarding a skip into an app that is still loading made regular YouTube show "An error occurred"
 * (11 Nov: forwarded while NONE at 08:47:24 and 08:49:30, ERROR 2 s later). A press that cannot be forwarded
 * safely is dropped, never queued: a missed press is harmless, a late skip is a wrong action.
 */
object ForwardGuard {
    /** Quiet time needed after a track change or after the app settles (PLAYING or PAUSED again). */
    const val SETTLE_MS = 2000L

    data class Decision(val ok: Boolean, val reason: String)

    /** [quietMs] = time since the app's last track change or return to a settled state; null if never seen. */
    fun check(state: Int?, quietMs: Long?): Decision {
        val settled = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
        if (!settled) {
            val name = SessionWatcher.stateName(state)
            return Decision(false, if (state == PlaybackState.STATE_ERROR) "app is in ERROR, leaving it alone"
                                   else "app is $name (loading or between tracks)")
        }
        if (quietMs != null && quietMs < SETTLE_MS) return Decision(false, "track changed ${quietMs} ms ago, app still settling")
        return Decision(true, "")
    }
}
