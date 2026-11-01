package com.nowsteering.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The running app: a MediaSession plus near-silent audio so the car shows our text,
 * fed by the nav / idle / test screens through [DisplayEngine].
 */
class SteeringService : Service() {
    companion object {
        @Volatile var instance: SteeringService? = null
        private const val CHANNEL = "ns"
        private const val NOTIF_ID = 1
        private const val REGRAB_MIN_INTERVAL_MS = 10_000L
        private const val ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS

        fun start(ctx: Context, why: String) {
            NsLog.log("SERVICE", "start requested: $why")
            try {
                ctx.startForegroundService(Intent(ctx, SteeringService::class.java))
            } catch (e: Exception) {
                NsLog.log("SERVICE", "start refused by Android: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        fun stop(ctx: Context, why: String) {
            NsLog.log("SERVICE", "stop requested: $why")
            ctx.stopService(Intent(ctx, SteeringService::class.java))
        }
    }

    enum class Mode { AUTO, IDLE_WHILE_NAV, TEST }

    /** How each text change reaches the car. METADATA_ONLY was proven parked with the phone unlocked. */
    enum class RefreshStyle(val label: String) {
        METADATA_ONLY("metadata only"),
        STATE_BUMP("metadata + playback-position bump"),
        TRACK_CHANGE("fake track change (new id each update)"),
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var session: MediaSession
    private lateinit var engine: DisplayEngine
    private lateinit var callGuard: CallGuard
    private lateinit var buttons: Buttons
    private var track: AudioTrack? = null
    private var lastRegrab = 0L
    private var lastInstruction: String? = null

    var mode = Mode.AUTO
        private set
    private var testScreen: Screen? = null
    /** Shown from arrival until the car moves, a new trip starts, double Next, or the car disconnects. */
    private var arrived: Screen? = null
    private var notice: Screen? = null
    private var noticeUntil = 0L

    private var speedKmh: Int? = null
    private var speedMps = 0f
    private var speedAt = 0L
    private var gpsStale = true

    // Dead reckoning: with the phone locked Maps only updates every 10-25 s, so between its
    // updates we count the turn distance down using our own GPS speed.
    private var drInstruction: String? = null
    private var drDistanceText: String? = null
    private var drBaseMeters: Double? = null
    private var drBaseAt = 0L
    private var drTravelled = 0.0
    private var lastNav: NavState.Info? = null
    private var locationOn = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var sendCount = 0L
    private val startedAt = SystemClock.elapsedRealtime()

    val currentScreen get() = engine.current
    val gesture get() = buttons.lastGesture
    val steppedAside get() = callGuard.inCall

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        audio = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "NowSteering").apply { setCallback(callback, main) }
        engine = DisplayEngine { s -> sendToCar(s) }
        buttons = Buttons(
            onNext = { forward("next") { it.skipToNext() } },
            onDoubleNext = { toggleNavIdle() },
            onPrevious = { forward("previous") { it.skipToPrevious() } },
            onPlayPause = { k -> togglePlayback(k) },
        )
        startForegroundNow()
        callGuard = CallGuard(audio, ::stepAside, ::comeBack)
        NavState.listener = { info -> main.post { onNav(info) } }
        startLocation()
        // Keep the CPU running with the screen off; only held while the car is connected.
        wakeLock = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "NowSteering:car").apply { acquire() }
        registerReceiver(screenReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(android.bluetooth.BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }, Context.RECEIVER_EXPORTED)
        logPowerSave("at start")
        NsLog.log("SERVICE", "started; ${NsApp.instance.permSummary()} refresh=${Prefs.refreshStyle}")
        if (!NsApp.instance.listenerEnabled()) NsLog.log("SERVICE", "WARNING: notification access is OFF: no nav, no music control")
        callGuard.start() // steps aside straight away if a call is already on
        if (!callGuard.inCall) comeBack()
        main.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        unregisterReceiver(screenReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        buttons.cancel()
        callGuard.stop()
        NavState.listener = null
        stopLocation()
        stopSilence()
        session.isActive = false
        session.release()
        instance = null
        NsLog.log("SERVICE", "stopped")
        super.onDestroy()
    }

    private fun hasLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Now Steering", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Now Steering is on")
            .setContentText("Showing nav / clock on the car screen")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        // Android 14+ refuses the location type without the permission, so only add it when granted.
        if (hasLocation()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        startForeground(NOTIF_ID, n, type)
    }

    // ---- Silent audio + session (never requests audio focus, so music keeps playing) ----

    private fun startSilence() {
        if (track != null) return
        val rate = 44100
        val frames = rate
        val pcm = ShortArray(frames * 2) { if ((it / 2) % 2 == 0) 0 else 1 } // ±1 LSB, inaudible
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
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
    }

    private fun stopSilence() {
        track?.let { it.stop(); it.release() }
        track = null
    }

    private fun setPlaying() {
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(ACTIONS)
            .setState(PlaybackState.STATE_PLAYING, 0, 1f)
            .build())
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_ON -> NsLog.log("PHONE", "screen on")
                Intent.ACTION_SCREEN_OFF -> NsLog.log("PHONE", "screen off")
                Intent.ACTION_USER_PRESENT -> NsLog.log("PHONE", "unlocked")
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> logPowerSave("changed")
                android.bluetooth.BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> {
                    val st = i.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1)
                    NsLog.log("BT", "A2DP audio stream " + if (st == android.bluetooth.BluetoothA2dp.STATE_PLAYING) "PLAYING" else "NOT_PLAYING ($st)")
                }
            }
        }
    }

    private fun logPowerSave(why: String) {
        val pm = getSystemService(android.os.PowerManager::class.java)
        // locationPowerSaveMode: 0 no change, 1 GPS off when screen off, 2 all location off when screen off, 3 foreground only, 4 throttled when screen off
        NsLog.log("PHONE", "power saving $why: on=${pm.isPowerSaveMode} locationMode=${pm.locationPowerSaveMode} " +
            "mapsBatteryExempt=${pm.isIgnoringBatteryOptimizations(NavListener.MAPS)}")
    }

    private fun sendToCar(s: Screen) {
        sendCount++
        val style = runCatching { RefreshStyle.valueOf(Prefs.refreshStyle) }.getOrDefault(RefreshStyle.METADATA_ONLY)
        val id = if (style == RefreshStyle.TRACK_CHANGE) "ns-$sendCount" else "ns"
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
            .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 3_600_000)
            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, 1)
            .build())
        when (style) {
            RefreshStyle.METADATA_ONLY -> {}
            RefreshStyle.STATE_BUMP -> session.setPlaybackState(PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(PlaybackState.STATE_PLAYING, SystemClock.elapsedRealtime() - startedAt, 1f)
                .build())
            RefreshStyle.TRACK_CHANGE -> setPlaying() // position back to 0 for the "new track"
        }
        NsLog.log("SCREEN", "sent $s${if (style != RefreshStyle.METADATA_ONLY) " [${style.name}]" else ""}")
    }

    /** Restart our silent track so Android ranks our session on top again. */
    fun regrab(why: String, force: Boolean = false) {
        if (callGuard.inCall) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRegrab < REGRAB_MIN_INTERVAL_MS) return
        lastRegrab = now
        NsLog.log("SESSION", "re-grab ($why)")
        stopSilence()
        startSilence()
        setPlaying()
        engine.current?.let { engine.show(it, force = true) }
    }

    // ---- Call safeguard ----

    private fun stepAside(why: String) {
        engine.pause()
        stopSilence()
        session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
        session.isActive = false
        NsLog.log("CALL", "STEPPED ASIDE ($why): silent audio stopped, session inactive, no text updates")
    }

    private fun comeBack() {
        session.isActive = true
        setPlaying()
        startSilence()
        lastRegrab = SystemClock.elapsedRealtime()
        engine.resume()
        refreshScreen(force = true)
        NsLog.log("SESSION", "active: silent audio on, session on top")
    }

    // ---- Screens ----

    private fun onNav(info: NavState.Info?) {
        if (info == null) {
            lastInstruction = null
            drBaseMeters = null
            if (mode == Mode.IDLE_WHILE_NAV) mode = Mode.AUTO
            val last = lastNav
            lastNav = null
            val left = last?.text?.remaining?.let { NavTextParser.metersOf(it) }
            if (left != null && left <= 150) {
                // Maps ends navigation by itself on arrival: say so instead of jumping straight to the clock.
                NsLog.log("SCREEN", "nav ended on arrival (${last.text.remaining} left): arrived screen until the car moves")
                arrived = Screen("● Arrived", last.text.road, clockFmt.format(Date()))
                refreshScreen()
            } else {
                NsLog.log("SCREEN", "nav ended before arrival (${last?.text?.remaining ?: "?"} left): idle screen")
                showNotice(Screen("Nav ended", "", ""), 4000)
            }
            return
        }
        lastNav = info
        notice = null // a new trip replaces any "Arrived" message
        clearArrived("new navigation")
        updateDeadReckoning(info)
        if (info.instructionId != lastInstruction) {
            val first = lastInstruction == null
            lastInstruction = info.instructionId
            regrab(if (first) "navigation started" else "new instruction", force = first)
        }
        refreshScreen()
    }

    private fun clearArrived(why: String) {
        if (arrived == null) return
        arrived = null
        NsLog.log("SCREEN", "arrived screen cleared ($why): clock screen")
        refreshScreen()
    }

    private fun toggleNavIdle() {
        if (mode == Mode.TEST) return
        if (arrived != null) {
            clearArrived("double Next")
            return
        }
        if (!NavState.active) {
            NsLog.log("SCREEN", "double Next: not navigating, staying on idle")
            return
        }
        mode = if (mode == Mode.AUTO) Mode.IDLE_WHILE_NAV else Mode.AUTO
        NsLog.log("SCREEN", "double Next: now showing ${if (mode == Mode.AUTO) "nav" else "idle"}")
        refreshScreen()
    }

    fun showTest(s: Screen) {
        mode = Mode.TEST
        testScreen = s
        NsLog.log("SCREEN", "test screen $s")
        refreshScreen()
    }

    fun backToLive() {
        mode = Mode.AUTO
        testScreen = null
        NsLog.log("SCREEN", "back to live screens")
        refreshScreen()
    }

    /** Show a short message on the car for [ms], then go back to the live screen. */
    private fun showNotice(s: Screen, ms: Long = 3000) {
        notice = s
        noticeUntil = SystemClock.elapsedRealtime() + ms
        refreshScreen()
    }

    private fun refreshScreen(force: Boolean = false) {
        val nav = NavState.info
        val s = when {
            notice != null && SystemClock.elapsedRealtime() < noticeUntil -> notice!!
            mode == Mode.TEST -> testScreen ?: idleScreen()
            arrived != null -> arrived!!
            nav != null && mode == Mode.AUTO -> navScreen(nav)
            else -> idleScreen()
        }
        engine.show(s, force)
    }

    /** Re-base the estimate whenever Maps gives a new instruction or a new distance; a repeated (stale) distance is ignored. */
    private fun updateDeadReckoning(i: NavState.Info) {
        val meters = NavTextParser.metersOf(i.text.distance)
        if (i.instructionId == drInstruction && i.text.distance == drDistanceText) return
        val est = estimatedMeters()
        if (est != null && i.instructionId == drInstruction && meters != null) {
            NsLog.log("DR", "Maps ${i.text.distance}, estimate was ${NavTextParser.formatMeters(est)} " +
                "(error ${(est - meters).roundToInt()} m after ${(SystemClock.elapsedRealtime() - drBaseAt) / 1000} s)")
        }
        drInstruction = i.instructionId
        drDistanceText = i.text.distance
        drBaseMeters = meters
        drBaseAt = SystemClock.elapsedRealtime()
        drTravelled = 0.0
    }

    /** Distance to the turn counted down from Maps' last value, or null when not estimating. */
    private fun estimatedMeters(): Double? {
        val base = drBaseMeters ?: return null
        if (drTravelled < 1 || SystemClock.elapsedRealtime() - drBaseAt > 90_000) return null
        return (base - drTravelled).coerceAtLeast(0.0)
    }

    private fun navScreen(i: NavState.Info): Screen {
        val t = i.text
        val distance = estimatedMeters()?.let { NavTextParser.formatMeters(it) } ?: t.distance
        val title = "${i.glyph} $distance".trim()
        val album = listOfNotNull(t.eta.takeIf { it.isNotEmpty() }?.let { "ETA $it" }, t.remaining.replace(" ", "").takeIf { it.isNotEmpty() })
            .joinToString(" ")
        return Screen(title, t.road, album)
    }

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.US)
    private val secFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun idleScreen(): Screen {
        val now = Date()
        val fresh = speedKmh != null && SystemClock.elapsedRealtime() - speedAt < 5000
        val speed = if (fresh) "$speedKmh" else "--"
        // With seconds (lock-screen refresh test) the title is 15 chars: "08:41:23 35km/h".
        val title = if (Prefs.idleSeconds) "${secFmt.format(now)} ${speed}km/h" else "${clockFmt.format(now)}  $speed km/h"
        return Screen(title, dateFmt.format(now), "Now Steering")
    }

    private val ticker = object : Runnable {
        override fun run() {
            checkGpsStale()
            refreshScreen() // engine skips it unless the text changed
            main.postDelayed(this, 1000)
        }
    }

    // ---- GPS speed ----

    private val locListener = LocationListener { loc: Location ->
        if (loc.hasSpeed()) {
            val now = SystemClock.elapsedRealtime()
            // Only count distance between fixes that are close together (GPS gaps are not guessed over).
            if (drBaseMeters != null && now - speedAt <= 3000) drTravelled += speedMps * (now - speedAt) / 1000.0
            speedMps = loc.speed
            val k = (loc.speed * 3.6f).roundToInt()
            speedKmh = if (k < 2) 0 else k
            speedAt = now
            if (gpsStale) { gpsStale = false; NsLog.log("GPS", "fixes coming in") }
            if (arrived != null && k > 10) clearArrived("moving at $k km/h")
        }
    }

    private fun checkGpsStale() {
        if (!gpsStale && SystemClock.elapsedRealtime() - speedAt > 5000) {
            gpsStale = true
            val pm = getSystemService(android.os.PowerManager::class.java)
            NsLog.log("GPS", "no fix for 5 s (powerSave=${pm.isPowerSaveMode} locationPowerSaveMode=${pm.locationPowerSaveMode})")
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // checked by hasLocation(); SecurityException caught
    private fun startLocation() {
        if (!hasLocation()) {
            NsLog.log("GPS", "no location permission: speed shows --")
            return
        }
        try {
            getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
            locationOn = true
            NsLog.log("GPS", "speed updates on")
        } catch (e: Exception) {
            NsLog.log("GPS", "could not start: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun stopLocation() {
        if (locationOn) getSystemService(LocationManager::class.java).removeUpdates(locListener)
        locationOn = false
    }

    // ---- Buttons -> music app ----

    private fun forward(what: String, action: (MediaController.TransportControls) -> Unit) {
        NsApp.watcher.refresh() // the session list can be stale
        val t = NsApp.watcher.target()
        if (t == null) {
            val access = NsApp.instance.listenerEnabled()
            NsLog.log("FWD", "$what: no music app session to forward to (notification access=$access)")
            // Tell the driver on the car screen why the button did nothing.
            showNotice(if (access) Screen("No music app", "Start music first", "") else Screen("No music link", "Grant notif access", "in Now Steering app"))
            return
        }
        val before = "${SessionWatcher.stateName(t.playbackState?.state)} ${SessionWatcher.describe(t.metadata)}"
        action(t.transportControls)
        NsLog.log("FWD", "$what -> ${t.packageName} (before: $before)")
        // Check a moment later whether the music app actually reacted.
        main.postDelayed({
            NsLog.log("FWD", "$what check: ${t.packageName} now ${SessionWatcher.stateName(t.playbackState?.state)} ${SessionWatcher.describe(t.metadata)}")
        }, 1500)
    }

    private fun togglePlayback(key: String) {
        // The head unit only ever sends PAUSE, so every pause press is a toggle.
        val playing = NsApp.watcher.target()?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) forward("pause ($key)") { it.pause() } else forward("play ($key)") { it.play() }
    }

    private val callback = object : MediaSession.Callback() {
        override fun onMediaButtonEvent(intent: Intent): Boolean {
            val ke = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            if (ke == null) {
                NsLog.log("KEY", "media button intent without KeyEvent")
                return super.onMediaButtonEvent(intent)
            }
            val action = if (ke.action == KeyEvent.ACTION_DOWN) "DOWN" else if (ke.action == KeyEvent.ACTION_UP) "UP" else "action${ke.action}"
            NsLog.log("KEY", "${KeyEvent.keyCodeToString(ke.keyCode)} (${ke.keyCode}) $action repeat=${ke.repeatCount}")
            return buttons.onKey(ke) || super.onMediaButtonEvent(intent)
        }

        // Only reached for commands that arrive without a KeyEvent.
        override fun onSkipToNext() { NsLog.log("KEY", "onSkipToNext without KeyEvent"); buttons.nextTap() }
        override fun onSkipToPrevious() { NsLog.log("KEY", "onSkipToPrevious without KeyEvent"); buttons.previous() }
        override fun onPause() { NsLog.log("KEY", "onPause without KeyEvent"); buttons.playPause("PAUSE") }
        override fun onPlay() { NsLog.log("KEY", "onPlay without KeyEvent"); buttons.playPause("PLAY") }
        override fun onStop() { NsLog.log("KEY", "onStop without KeyEvent (ignored)") }
    }
}
