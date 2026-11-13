package com.nowsteering.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Takes screens, sanitises them, and hands them to [sink] only when the text changed,
 * at most once per second. The newest pending screen wins.
 */
class DisplayEngine(private val sink: (Screen) -> Unit) {
    companion object {
        const val MIN_INTERVAL_MS = CarProfile.HONDA_REFRESH_MS
    }

    private val main = Handler(Looper.getMainLooper())
    private var lastSent: Screen? = null
    private var lastSentAt = 0L
    private var pending: Screen? = null
    var paused = false
        private set

    val current: Screen? get() = lastSent

    private val flush = Runnable { pending?.let { send(it) } }

    fun show(screen: Screen, force: Boolean = false) {
        val s = screen.sanitized()
        if (!force && s == lastSent) {
            // Already on the car; drop anything older that was waiting.
            pending = null
            main.removeCallbacks(flush)
            return
        }
        pending = s
        if (paused) return
        main.removeCallbacks(flush)
        val wait = lastSentAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait <= 0) send(s) else main.postDelayed(flush, wait)
    }

    private fun send(s: Screen) {
        pending = null
        if (paused) return
        lastSent = s
        lastSentAt = SystemClock.elapsedRealtime()
        sink(s)
    }

    /** Stop sending (e.g. during a call). The latest screen is kept and re-sent on [resume]. */
    fun pause() {
        paused = true
        main.removeCallbacks(flush)
        if (pending == null) pending = lastSent
    }

    fun resume() {
        paused = false
        val s = pending ?: lastSent ?: return
        lastSent = null // force a re-send
        show(s, force = true)
    }
}
