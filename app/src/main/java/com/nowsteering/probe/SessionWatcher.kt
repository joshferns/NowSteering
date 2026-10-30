package com.nowsteering.probe

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/** Lists all active media sessions (needs notification-listener access) and logs their changes. */
class SessionWatcher(private val ctx: Context) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(ctx, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var lastOrder = ""

    @Volatile var controllers: List<MediaController> = emptyList()
        private set
    private val callbacks = HashMap<android.media.session.MediaSession.Token, MediaController.Callback>()
    private val lastState = HashMap<android.media.session.MediaSession.Token, Int>()

    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> update(list ?: emptyList()) }

    fun start() {
        if (started) return
        try {
            msm.addOnActiveSessionsChangedListener(changed, component, main)
            started = true
            ProbeLog.log("SESSIONS", "watching active media sessions")
            update(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            ProbeLog.log("SESSIONS", "cannot list sessions: notification-listener access not granted")
        }
    }

    /** Re-reads the list on demand (the system callback can be lazy). */
    fun refresh() {
        if (!started) { start(); return }
        try { update(msm.getActiveSessions(component)) } catch (e: SecurityException) { }
    }

    private fun update(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it !in tokens }.forEach { t ->
            controllers.firstOrNull { it.sessionToken == t }?.unregisterCallback(callbacks[t]!!)
            callbacks.remove(t); lastState.remove(t)
        }
        for (c in list) {
            if (c.sessionToken in callbacks) continue
            val cb = makeCallback(c)
            c.registerCallback(cb, main)
            callbacks[c.sessionToken] = cb
            lastState[c.sessionToken] = c.playbackState?.state ?: -1
        }
        controllers = list
        val order = list.joinToString(" > ") { "${short(it.packageName)}:${stateName(it.playbackState?.state)}" }
        if (order != lastOrder) {
            lastOrder = order
            ProbeLog.log("SESSIONS", "active order (first = top priority): ${order.ifEmpty { "none" }}")
        }
    }

    private fun makeCallback(c: MediaController) = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val s = state?.state ?: -1
            if (lastState[c.sessionToken] == s) return // ignore position-only updates
            lastState[c.sessionToken] = s
            ProbeLog.log("SESSIONS", "${short(c.packageName)} state -> ${stateName(s)}")
            refresh()
        }

        override fun onMetadataChanged(md: MediaMetadata?) {
            if (c.packageName == ctx.packageName) return // our own pushes are already logged
            ProbeLog.log("SESSIONS", "${short(c.packageName)} metadata: ${describe(md)}")
            if (ProbeState.mirrorTarget && c.sessionToken == target()?.sessionToken) {
                ProbeService.instance?.mirror(md)
            }
        }

        override fun onSessionDestroyed() {
            ProbeLog.log("SESSIONS", "${short(c.packageName)} session destroyed")
            refresh()
        }
    }

    /** The music app we coexist with: YouTube Music if present, else the first other app. */
    fun target(): MediaController? =
        controllers.firstOrNull { it.packageName == ProbeState.YTM_PACKAGE }
            ?: controllers.firstOrNull { it.packageName != ctx.packageName }

    fun summary(): String {
        if (!started) return "Session list unavailable: grant notification-listener access."
        if (controllers.isEmpty()) return "No active media sessions."
        val t = target()?.sessionToken
        return controllers.mapIndexed { i, c ->
            val mark = when {
                c.packageName == ctx.packageName -> " (US)"
                c.sessionToken == t -> " (TARGET)"
                else -> ""
            }
            "${i + 1}. ${c.packageName}$mark\n   ${stateName(c.playbackState?.state)} | ${describe(c.metadata)}"
        }.joinToString("\n")
    }

    companion object {
        fun short(pkg: String) = when (pkg) {
            ProbeState.YTM_PACKAGE -> "YTMusic"
            "com.nowsteering.probe" -> "US"
            else -> pkg
        }

        fun stateName(s: Int?) = when (s) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_NONE -> "NONE"
            PlaybackState.STATE_CONNECTING -> "CONNECTING"
            PlaybackState.STATE_ERROR -> "ERROR"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIP_NEXT"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIP_PREV"
            null, -1 -> "no-state"
            else -> "state$s"
        }

        fun describe(md: MediaMetadata?): String {
            if (md == null) return "no metadata"
            return "title='${md.getString(MediaMetadata.METADATA_KEY_TITLE)}' " +
                "artist='${md.getString(MediaMetadata.METADATA_KEY_ARTIST)}' " +
                "album='${md.getString(MediaMetadata.METADATA_KEY_ALBUM)}'"
        }
    }
}
