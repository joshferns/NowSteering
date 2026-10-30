package com.nowsteering.probe

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns raw key DOWN/UP pairs into single / double / triple tap and long hold, per key.
 * Taps are grouped while the gap between an UP and the next DOWN is under [ProbeState.tapWindowMs];
 * a DOWN still held after [ProbeState.holdThresholdMs] is a long hold.
 */
object Gestures {
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private var key = 0          // key code of the gesture in progress, 0 = none
    private var taps = 0         // completed taps so far
    private var down = false
    private var downAt = 0L
    private var holdFired = false
    private var source = ""

    private val finishTaps = Runnable { emit(tapName(taps)) }
    private val holdCheck = Runnable {
        if (down) {
            holdFired = true
            emit(if (taps == 0) "LONG HOLD" else "${tapName(taps)} + HOLD", reset = false)
        }
    }

    private fun tapName(n: Int) = when (n) {
        1 -> "SINGLE"
        2 -> "DOUBLE"
        3 -> "TRIPLE"
        else -> "$n-TAP"
    }

    private fun label(code: Int) = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_MEDIA_").removePrefix("KEYCODE_")

    fun onKey(ke: KeyEvent) {
        if (ke.action == KeyEvent.ACTION_DOWN) {
            if (ke.repeatCount > 0) return // auto-repeat while held; raw log keeps it
            press(ke.keyCode, SystemClock.uptimeMillis(), "key")
        } else if (ke.action == KeyEvent.ACTION_UP) {
            release(ke.keyCode, SystemClock.uptimeMillis())
        }
    }

    /** A transport command (onSkipToNext etc.) that arrived without any KeyEvent: count it as an instant tap. */
    fun onTransportOnly(code: Int) {
        val now = SystemClock.uptimeMillis()
        press(code, now, "transport-only")
        release(code, now)
    }

    private fun press(code: Int, t: Long, src: String) {
        if (key != 0 && code != key) finishNow()
        main.removeCallbacks(finishTaps)
        key = code; down = true; downAt = t; holdFired = false; source = src
        main.postDelayed(holdCheck, ProbeState.holdThresholdMs.toLong())
    }

    private fun release(code: Int, t: Long) {
        if (code != key || !down) return
        down = false
        main.removeCallbacks(holdCheck)
        if (holdFired) {
            ProbeLog.log("GESTURE", "${label(key)} hold released after ${t - downAt}ms")
            reset()
            return
        }
        taps++
        main.postDelayed(finishTaps, ProbeState.tapWindowMs.toLong())
    }

    private fun finishNow() {
        main.removeCallbacks(finishTaps); main.removeCallbacks(holdCheck)
        if (taps > 0 && !holdFired) emit(tapName(taps)) else reset()
    }

    private fun emit(name: String, reset: Boolean = true) {
        val text = "${label(key)} $name"
        ProbeLog.log("GESTURE", "$text (tap window ${ProbeState.tapWindowMs}ms, hold ${ProbeState.holdThresholdMs}ms, via $source)")
        ProbeState.gesture = "$text\n${fmt.format(Date())}"
        if (reset) reset()
    }

    private fun reset() {
        key = 0; taps = 0; down = false; holdFired = false
    }
}
