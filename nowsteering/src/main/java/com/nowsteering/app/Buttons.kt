package com.nowsteering.app

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Steering buttons. The Honda sends an instant DOWN+UP per press and never sends holds or PLAY,
 * so we act on DOWN only. Next waits [Prefs.tapWindowMs] to count taps (single = forward next,
 * double = switch nav/idle screen); Previous and Pause act immediately. Only DOWN counts: a lone UP,
 * which arrives when the other app took the DOWN, never does anything.
 */
class Buttons(
    private val onNext: () -> Unit,
    private val onDoubleNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onPlayPause: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var nextTaps = 0

    /** Last recognised gesture, shown in Diagnostics. */
    @Volatile var lastGesture = "(no button yet)"
        private set

    private val finishNext = Runnable {
        val n = nextTaps
        nextTaps = 0
        when (n) {
            1 -> gesture("NEXT single") { onNext() }
            2 -> gesture("NEXT double") { onDoubleNext() }
            else -> gesture("NEXT x$n (ignored)") {}
        }
    }

    private fun gesture(name: String, action: () -> Unit) {
        lastGesture = "$name\n${fmt.format(Date())}"
        NsLog.log("GESTURE", name)
        action()
    }

    /** Returns true when the key was handled. */
    fun onKey(ke: KeyEvent): Boolean {
        if (ke.action != KeyEvent.ACTION_DOWN || ke.repeatCount > 0) return true
        when (ke.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> nextTap()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> gesture("PREVIOUS") { onPrevious() }
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                val k = KeyEvent.keyCodeToString(ke.keyCode).removePrefix("KEYCODE_MEDIA_").removePrefix("KEYCODE_")
                gesture(k) { onPlayPause(k) }
            }
            else -> {
                NsLog.log("GESTURE", "unhandled key ${KeyEvent.keyCodeToString(ke.keyCode)}")
                return false
            }
        }
        return true
    }

    /** Transport commands that arrive without a KeyEvent go through the same paths. */
    fun nextTap() {
        nextTaps++
        main.removeCallbacks(finishNext)
        main.postDelayed(finishNext, Prefs.tapWindowMs.toLong())
    }

    fun previous() = gesture("PREVIOUS (transport)") { onPrevious() }
    fun playPause(k: String) = gesture("$k (transport)") { onPlayPause(k) }

    fun cancel() = main.removeCallbacks(finishNext)
}
