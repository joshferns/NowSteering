package com.nowsteering.app

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/** Lists active media sessions (needs notification-listener access), logs changes, picks the music app to forward to. */
class SessionWatcher(private val ctx: Context) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(ctx, NavListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var deniedLogged = false
    private var lastOrder = ""
    private var lastPlayingPackage: String? = null

    @Volatile var controllers: List<MediaController> = emptyList()
        private set
    private val callbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    private val lastState = HashMap<MediaSession.Token, Int>()
    private val lastTitle = HashMap<MediaSession.Token, String?>()

    /** A music app changed song (it may have taken the car screen). */
    var onSongChange: ((String) -> Unit)? = null

    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> update(list ?: emptyList()) }

    fun start() {
        if (started) return
        try {
            msm.addOnActiveSessionsChangedListener(changed, component, main)
            started = true
            NsLog.log("SESSIONS", "watching active media sessions")
            update(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            if (!deniedLogged) NsLog.log("SESSIONS", "cannot list sessions: notification-listener access not granted")
            deniedLogged = true
        }
    }

    fun refresh() {
        if (!started) { start(); return }
        try { update(msm.getActiveSessions(component)) } catch (e: SecurityException) { }
    }

    private fun isMusic(c: MediaController) = c.packageName != ctx.packageName && c.packageName != "com.android.server.telecom"

    private fun update(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it !in tokens }.forEach { t ->
            controllers.firstOrNull { it.sessionToken == t }?.unregisterCallback(callbacks[t]!!)
            callbacks.remove(t); lastState.remove(t); lastTitle.remove(t)
        }
        for (c in list) {
            if (c.sessionToken in callbacks) continue
            val cb = makeCallback(c)
            c.registerCallback(cb, main)
            callbacks[c.sessionToken] = cb
            lastState[c.sessionToken] = c.playbackState?.state ?: -1
            lastTitle[c.sessionToken] = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (isMusic(c) && c.playbackState?.state == PlaybackState.STATE_PLAYING) lastPlayingPackage = c.packageName
        }
        controllers = list
        val order = list.joinToString(" > ") { "${short(it.packageName)}:${stateName(it.playbackState?.state)}" }
        if (order != lastOrder) {
            lastOrder = order
            NsLog.log("SESSIONS", "active order (first = top priority): ${order.ifEmpty { "none" }}")
        }
    }

    private fun makeCallback(c: MediaController) = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val s = state?.state ?: -1
            if (lastState[c.sessionToken] == s) return
            lastState[c.sessionToken] = s
            NsLog.log("SESSIONS", "${short(c.packageName)} state -> ${stateName(s)}")
            if (isMusic(c) && s == PlaybackState.STATE_PLAYING) lastPlayingPackage = c.packageName
            refresh()
        }

        override fun onMetadataChanged(md: MediaMetadata?) {
            if (!isMusic(c)) return
            NsLog.log("SESSIONS", "${short(c.packageName)} metadata: ${describe(md)}")
            // Music apps resend the same metadata several times; only a new title is a song change.
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (!title.isNullOrEmpty() && lastTitle.containsKey(c.sessionToken) && lastTitle[c.sessionToken] != title) {
                onSongChange?.invoke(c.packageName)
            }
            if (!title.isNullOrEmpty()) lastTitle[c.sessionToken] = title
        }

        override fun onSessionDestroyed() {
            NsLog.log("SESSIONS", "${short(c.packageName)} session destroyed")
            refresh()
        }
    }

    /** Music app to forward buttons to: one that is playing, else the last one that played, else any. */
    fun target(): MediaController? {
        val music = controllers.filter { isMusic(it) }
        return music.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: music.firstOrNull { it.packageName == lastPlayingPackage }
            ?: music.firstOrNull()
    }

    fun summary(): String {
        if (!started) return "Session list unavailable: grant notification access."
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
            "com.google.android.apps.youtube.music" -> "YTMusic"
            "com.nowsteering.app" -> "US"
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
            return "title='${md.getString(MediaMetadata.METADATA_KEY_TITLE)}' artist='${md.getString(MediaMetadata.METADATA_KEY_ARTIST)}'"
        }
    }
}
