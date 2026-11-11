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
import android.os.SystemClock

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
    /** Last track change, or last return to PLAYING/PAUSED after loading: forwarding a skip right then breaks some players. */
    private val quietSince = HashMap<MediaSession.Token, Long>()

    /** A music app changed song (it may have taken the car screen). */
    var onSongChange: ((String) -> Unit)? = null

    /** Another app started (or resumed) playing: it is now the app the car follows, until we re-claim. */
    var onOtherStartedPlaying: ((String) -> Unit)? = null

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

    /** A browser tab's media session ("Stories • Instagram") is not a song. */
    private fun isSongSource(c: MediaController) = isMusic(c) && c.packageName !in BROWSERS

    private fun isActive(state: Int?) = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    /** Milliseconds since [c] last changed track or settled, or null if neither has been seen. */
    fun quietMs(c: MediaController): Long? = quietSince[c.sessionToken]?.let { SystemClock.elapsedRealtime() - it }

    /** Is some app other than us playing right now (it would hold the car's screen and buttons)? */
    fun otherIsPlaying() = controllers.any { isMusic(it) && isActive(it.playbackState?.state) }

    /** What the car's owner is listening to right now. */
    data class Song(val pkg: String, val title: String, val artist: String, val durationMs: Long, val positionMs: Long?)

    fun song(): Song? {
        val c = controllers.firstOrNull { isSongSource(it) && isActive(it.playbackState?.state) } ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val ps = c.playbackState
        var pos: Long? = null
        if (ps != null && ps.position >= 0) {
            val advance = if (ps.state == PlaybackState.STATE_PLAYING)
                ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong().coerceAtLeast(0) else 0L
            pos = ps.position + advance
        }
        return Song(c.packageName, title, md.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty(),
            md.getLong(MediaMetadata.METADATA_KEY_DURATION), pos)
    }

    private fun update(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it !in tokens }.forEach { t ->
            controllers.firstOrNull { it.sessionToken == t }?.unregisterCallback(callbacks[t]!!)
            callbacks.remove(t); lastState.remove(t); lastTitle.remove(t); quietSince.remove(t)
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
            val prev = lastState[c.sessionToken]
            if (prev == s) return
            lastState[c.sessionToken] = s
            val settled = s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED
            val wasSettled = prev == PlaybackState.STATE_PLAYING || prev == PlaybackState.STATE_PAUSED
            if (settled && !wasSettled) quietSince[c.sessionToken] = SystemClock.elapsedRealtime()
            NsLog.log("SESSIONS", "${short(c.packageName)} state -> ${stateName(s)}")
            if (isMusic(c) && s == PlaybackState.STATE_PLAYING) lastPlayingPackage = c.packageName
            if (isMusic(c) && isActive(s)) onOtherStartedPlaying?.invoke(c.packageName)
            refresh()
        }

        override fun onMetadataChanged(md: MediaMetadata?) {
            if (!isMusic(c)) return
            NsLog.log("SESSIONS", "${short(c.packageName)} metadata: ${describe(md)}")
            // Music apps resend the same metadata several times; only a new title is a song change.
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (!title.isNullOrEmpty() && lastTitle.containsKey(c.sessionToken) && lastTitle[c.sessionToken] != title) {
                quietSince[c.sessionToken] = SystemClock.elapsedRealtime()
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
        private val BROWSERS = setOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox",
            "com.brave.browser", "com.microsoft.emmx", "com.opera.browser")

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
