package com.nowsteering.app

import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Watches AudioManager's mode (no phone-state permission). Any non-normal mode (ringing, in call,
 * VoIP) makes us step aside at once; we come back [RESUME_DELAY_MS] after the mode is normal again.
 * A 1 s poll backs up the listener in case a change is missed.
 */
class CallGuard(
    private val audio: AudioManager,
    private val stepAside: (String) -> Unit,
    private val comeBack: () -> Unit,
) {
    companion object {
        const val RESUME_DELAY_MS = 3000L

        fun modeName(m: Int) = when (m) {
            AudioManager.MODE_NORMAL -> "NORMAL"
            AudioManager.MODE_RINGTONE -> "RINGTONE"
            AudioManager.MODE_IN_CALL -> "IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
            AudioManager.MODE_CALL_SCREENING -> "CALL_SCREENING"
            4 -> "CALL_REDIRECT"
            5 -> "COMMUNICATION_REDIRECT"
            else -> "mode$m"
        }
    }

    private val main = Handler(Looper.getMainLooper())
    var inCall = false
        private set
    private var lastMode = AudioManager.MODE_NORMAL

    private val resume = Runnable {
        if (audio.mode == AudioManager.MODE_NORMAL) {
            inCall = false
            NsLog.log("CALL", "mode NORMAL for ${RESUME_DELAY_MS}ms: coming back")
            comeBack()
        } else {
            NsLog.log("CALL", "resume cancelled: mode is ${modeName(audio.mode)}")
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { m -> main.post { onMode(m, "listener") } }

    private val poll = object : Runnable {
        override fun run() {
            onMode(audio.mode, "poll")
            main.postDelayed(this, 1000)
        }
    }

    fun start() {
        audio.addOnModeChangedListener({ it.run() }, modeListener)
        lastMode = -1
        onMode(audio.mode, "start")
        main.postDelayed(poll, 1000)
    }

    fun stop() {
        audio.removeOnModeChangedListener(modeListener)
        main.removeCallbacks(poll)
        main.removeCallbacks(resume)
    }

    private fun onMode(m: Int, via: String) {
        if (m == lastMode) return
        val prev = lastMode
        lastMode = m
        NsLog.log("CALL", "audio mode ${if (prev < 0) "" else modeName(prev) + " -> "}${modeName(m)} (via $via)")
        if (m != AudioManager.MODE_NORMAL) {
            main.removeCallbacks(resume)
            if (!inCall) {
                inCall = true
                stepAside(modeName(m))
            }
        } else if (inCall) {
            NsLog.log("CALL", "call over: waiting ${RESUME_DELAY_MS}ms before coming back")
            main.removeCallbacks(resume)
            main.postDelayed(resume, RESUME_DELAY_MS)
        }
    }
}
