package com.nowsteering.probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Foreground service holding our MediaSession and a near-silent looping AudioTrack, so the
 * phone (and the car over AVRCP) treats us as the active player.
 */
class ProbeService : Service() {
    companion object {
        @Volatile var instance: ProbeService? = null
        private const val CHANNEL = "probe"
        private const val NOTIF_ID = 1
        private const val ALL_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_SEEK_TO
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    private var trackNo = 1
    private var durationMs = 200_000L
    private var trackStart = SystemClock.elapsedRealtime()
    private var playing = true

    private var lastKeyTime = 0L // KeyEvent.eventTime, uptime base

    /** True if a KeyEvent arrived recently, i.e. a transport callback probably came from it. */
    private fun keyRecent() = lastKeyTime != 0L && SystemClock.uptimeMillis() - lastKeyTime < 1000

    private var autoTask: Runnable? = null
    var autoCount = 0
        private set
    val autoRunning get() = autoTask != null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        audio = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "NowSteeringProbe").apply {
            setCallback(callback, main)
            isActive = true
        }
        startForegroundNow()
        setState(PlaybackState.STATE_PLAYING)
        pushMetadata("session start", ProbeState.RefreshMode.FAKE_TRACK_CHANGE)
        startSilence()
        ProbeLog.log("SESSION", "started (audio focus request=${ProbeState.requestAudioFocus})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onDestroy() {
        stopAuto()
        stopSilence()
        session.isActive = false
        session.release()
        instance = null
        ProbeLog.log("SESSION", "stopped")
        super.onDestroy()
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Probe session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("NS Probe session running")
            .setContentText("Silent audio holds the media session")
            .setContentIntent(open)
            .setOngoing(true)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    // ---- Silent audio ------------------------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { f ->
        val name = when (f) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            else -> "focus$f"
        }
        // We deliberately keep playing silence on loss: that is part of the coexistence probe.
        ProbeLog.log("AUDIO", "our audio focus -> $name (silence keeps playing)")
    }

    private fun startSilence() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (ProbeState.requestAudioFocus) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build()
            focusRequest = req
            val r = audio.requestAudioFocus(req)
            ProbeLog.log("AUDIO", "requested audio focus: " + if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "result $r")
        } else {
            ProbeLog.log("AUDIO", "NOT requesting audio focus (coexistence mode)")
        }
        val rate = 44100
        val frames = rate // one second, looped forever
        // +/-1 LSB pattern (about -90 dB): inaudible, but not digital zero in case anything detects silence.
        val pcm = ShortArray(frames * 2) { if ((it / 2) % 2 == 0) 0 else 1 }
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, frames, -1)
        t.play()
        track = t
        ProbeLog.log("AUDIO", "silent track playing (state=${t.playState})")
    }

    private fun stopSilence() {
        track?.let { it.stop(); it.release() }
        track = null
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Restart audio + re-activate the session, to try to become the top session again. */
    fun regrab() {
        ProbeLog.log("SESSION", "re-grab: restarting silent track and re-activating session")
        stopSilence()
        session.isActive = false
        session.isActive = true
        setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        startSilence()
    }

    // ---- Metadata / state --------------------------------------------------

    private fun position() = (SystemClock.elapsedRealtime() - trackStart).coerceIn(0, durationMs)

    private fun setState(state: Int) {
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(ALL_ACTIONS)
            .setState(state, position(), if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
            .build())
    }

    fun pushMetadata(reason: String, mode: ProbeState.RefreshMode = ProbeState.mode) {
        if (mode == ProbeState.RefreshMode.FAKE_TRACK_CHANGE) {
            trackNo++
            durationMs = Random.nextLong(60_000, 400_000) / 1000 * 1000
            trackStart = SystemClock.elapsedRealtime()
        }
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "probe-$trackNo")
            .putString(MediaMetadata.METADATA_KEY_TITLE, ProbeState.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, ProbeState.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, ProbeState.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, trackNo.toLong())
            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, 99)
            .build()
        val current = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        when (mode) {
            ProbeState.RefreshMode.TOGGLE_PLAYBACK -> {
                setState(PlaybackState.STATE_PAUSED)
                session.setMetadata(md)
                // Short gap so the Bluetooth stack sees two distinct state changes.
                main.postDelayed({ setState(current) }, 300)
            }
            ProbeState.RefreshMode.FAKE_TRACK_CHANGE -> {
                session.setMetadata(md)
                setState(current) // position back to 0
            }
            ProbeState.RefreshMode.METADATA_ONLY -> session.setMetadata(md)
        }
        ProbeLog.log("META", "send [$reason] mode=${mode.name} id=probe-$trackNo dur=${durationMs / 1000}s " +
            "title='${ProbeState.title}' artist='${ProbeState.artist}' album='${ProbeState.album}'")
    }

    fun mirror(md: MediaMetadata?) {
        if (md == null) return
        val t = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val a = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val al = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        if (t == ProbeState.title && a == ProbeState.artist && al == ProbeState.album) return
        ProbeState.title = t; ProbeState.artist = a; ProbeState.album = al
        pushMetadata("mirror")
    }

    // ---- Auto counter ------------------------------------------------------

    fun startAuto(seconds: Int) {
        stopAuto()
        autoCount = 0
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val task = object : Runnable {
            override fun run() {
                autoCount++
                ProbeState.title = "#$autoCount ${fmt.format(Date())}"
                pushMetadata("auto ${seconds}s")
                main.postDelayed(this, seconds * 1000L)
            }
        }
        autoTask = task
        ProbeLog.log("AUTO", "auto counter every ${seconds}s, mode=${ProbeState.mode.name}")
        main.post(task)
    }

    fun stopAuto() {
        autoTask?.let {
            main.removeCallbacks(it)
            ProbeLog.log("AUTO", "auto counter stopped at #$autoCount")
        }
        autoTask = null
    }

    // ---- Buttons -----------------------------------------------------------

    private fun flash(s: String) {
        ProbeState.flash = s
    }

    private fun forward(what: String, action: (android.media.session.MediaController.TransportControls) -> Unit): Boolean {
        if (!ProbeState.forwardToTarget) return false
        val t = ProbeApp.watcher.target()
        if (t == null) {
            ProbeLog.log("FWD", "$what: no target session to forward to")
            return false
        }
        action(t.transportControls)
        ProbeLog.log("FWD", "$what -> ${t.packageName}")
        return true
    }

    private fun command(name: String, block: () -> Unit = {}) {
        ProbeLog.log("CMD", name + if (keyRecent()) "" else " (no KeyEvent: direct transport control)")
        flash("$name\n${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())}")
        block()
    }

    private val callback = object : MediaSession.Callback() {
        override fun onMediaButtonEvent(intent: Intent): Boolean {
            @Suppress("DEPRECATION")
            val ke: KeyEvent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            if (ke != null) {
                val action = when (ke.action) {
                    KeyEvent.ACTION_DOWN -> "DOWN"
                    KeyEvent.ACTION_UP -> "UP"
                    else -> "action${ke.action}"
                }
                val gap = if (lastKeyTime == 0L) "-" else "${ke.eventTime - lastKeyTime}ms"
                lastKeyTime = ke.eventTime
                val msg = "${KeyEvent.keyCodeToString(ke.keyCode)} (${ke.keyCode}) $action repeat=${ke.repeatCount} " +
                    "held=${ke.eventTime - ke.downTime}ms gapSincePrevKey=$gap eventTime=${ke.eventTime} " +
                    "longPress=${ke.isLongPress} flags=0x${Integer.toHexString(ke.flags)} " +
                    "dev=${ke.deviceId} src=0x${Integer.toHexString(ke.source)}"
                ProbeLog.log("KEY", msg)
                Gestures.onKey(ke)
                flash("${KeyEvent.keyCodeToString(ke.keyCode).removePrefix("KEYCODE_")} $action r${ke.repeatCount}\n" +
                    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()))
            } else {
                ProbeLog.log("KEY", "media button intent without KeyEvent: $intent")
            }
            return super.onMediaButtonEvent(intent) // also dispatches to onPlay/onPause/... below
        }

        override fun onPlay() = command("onPlay") {
            if (!forward("play") { it.play() }) {
                playing = true; setState(PlaybackState.STATE_PLAYING)
            }
        }

        override fun onPause() = command("onPause") {
            // Silence keeps playing either way so we stay the active player.
            if (!forward("pause") { it.pause() }) {
                playing = false; setState(PlaybackState.STATE_PAUSED)
            }
        }

        override fun onSkipToNext() = command("onSkipToNext") {
            if (!keyRecent()) Gestures.onTransportOnly(KeyEvent.KEYCODE_MEDIA_NEXT)
            forward("next") { it.skipToNext() }
        }

        override fun onSkipToPrevious() = command("onSkipToPrevious") {
            if (!keyRecent()) Gestures.onTransportOnly(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            forward("previous") { it.skipToPrevious() }
        }
        override fun onStop() = command("onStop") { forward("stop") { it.stop() } }
        override fun onFastForward() = command("onFastForward") { forward("fastForward") { it.fastForward() } }
        override fun onRewind() = command("onRewind") { forward("rewind") { it.rewind() } }
        override fun onSeekTo(pos: Long) = command("onSeekTo $pos") { forward("seekTo") { it.seekTo(pos) } }
        override fun onPlayFromMediaId(id: String?, extras: Bundle?) = command("onPlayFromMediaId $id")
        override fun onPlayFromSearch(q: String?, extras: Bundle?) = command("onPlayFromSearch '$q'")
        override fun onSkipToQueueItem(id: Long) = command("onSkipToQueueItem $id")
        override fun onCustomAction(action: String, extras: Bundle?) = command("onCustomAction $action")
        override fun onPrepare() = command("onPrepare")
    }
}
