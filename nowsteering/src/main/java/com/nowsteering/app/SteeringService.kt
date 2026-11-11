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
        const val ACTION_SHOW_ON_CAR = "com.nowsteering.app.SHOW_ON_CAR"
        private const val CLAIM_DELAY_MS = 1500L      // let the other app finish starting its player first
        private const val CLAIM_MIN_GAP_MS = 2000L    // never more than one automatic claim per 2 s
        private const val CLAIM_MAX_PER_30S = 4       // ... and back off if something keeps taking it back
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
    private var lastKeyAt = 0L // when a media button last reached us (it only does while we hold the car)
    private val zone = TurnZone()
    private var heldSong: SessionWatcher.Song? = null
    private var heldSongAt = 0L

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

    // Distance to the turn: Maps' value, counted down with GPS speed when Maps goes quiet (phone locked).
    private val dr = DeadReckoner { SystemClock.elapsedRealtime() }
    private var lastNav: NavState.Info? = null
    /** The instruction on the car; can lag NavState.info briefly while a new route settles. */
    private var shownNav: NavState.Info? = null
    private var navStartAt = 0L
    private var pendingNav: NavState.Info? = null
    private var arrivedAt = 0L
    private var arrivedLoc: Location? = null
    private var lastLoc: Location? = null
    private var navEndedSinceArrival = false
    private var stoppedSinceArrival = false // arrival is detected ~20 m out, often still rolling
    private var tripMeters: Double? = null // longest distance-left seen this trip, for the trip bar
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
        NsApp.watcher.onSongChange = { pkg -> claimSoon("song change in ${SessionWatcher.short(pkg)}") }
        NsApp.watcher.onOtherStartedPlaying = { pkg -> claimSoon("${SessionWatcher.short(pkg)} started playing") }
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
        powerSaveWarning("service start")
        main.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_ON_CAR) claimNow("notification button")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        unregisterReceiver(screenReceiver)
        wakeLock?.let { if (it.isHeld) it.release() }
        buttons.cancel()
        callGuard.stop()
        NavState.listener = null
        NsApp.watcher.onSongChange = null
        NsApp.watcher.onOtherStartedPlaying = null
        main.removeCallbacks(claim)
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
        val show = PendingIntent.getService(this, 1,
            Intent(this, SteeringService::class.java).setAction(ACTION_SHOW_ON_CAR), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Now Steering is on")
            .setContentText("Showing nav / clock on the car screen")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_view), "Show on car", show).build())
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
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> { logPowerSave("changed"); powerSaveWarning("turned on") }
                android.bluetooth.BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> {
                    val st = i.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1)
                    NsLog.log("BT", "A2DP audio stream " + if (st == android.bluetooth.BluetoothA2dp.STATE_PLAYING) "PLAYING" else "NOT_PLAYING ($st)")
                }
            }
        }
    }

    /** Power saving stops our GPS with the screen off: say so on the car instead of degrading silently. */
    private fun powerSaveWarning(why: String) {
        if (!getSystemService(android.os.PowerManager::class.java).isPowerSaveMode) return
        NsLog.log("PHONE", "power saving is on ($why): warning on the car")
        showNotice(Screen("Power saving on", "nav less smooth", "turn it off"), 6000)
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

    // ---- Re-claiming the car ----
    // Android 13 sends both the car's "now playing" screen and the steering-wheel buttons to the app that
    // most recently STARTED playing audio. Restarting our own silent audio (re-grab) puts us back in front.

    private var claimWhy = ""
    private var claimEvents = 0
    private var claimPending = false
    private var lastClaimAt = 0L
    private val claimTimes = ArrayDeque<Long>()
    private val claim = Runnable { runClaim() }

    private fun keyAgo() = if (lastKeyAt == 0L) "never" else "${(SystemClock.elapsedRealtime() - lastKeyAt) / 1000} s ago"

    /**
     * Another app may have taken the car. While navigating, or with "Keep Now Steering on screen" on, take it back
     * ~1.5 s later (a burst of events becomes one claim), at most once per 2 s and 4 per 30 s.
     */
    private fun claimSoon(why: String) {
        if (!NavState.active && !Prefs.keepOnScreen) {
            NsLog.log("CLAIM", "no re-claim after $why: not navigating and 'Keep on screen' is off, music info stays on the car; last button to us ${keyAgo()}")
            return
        }
        if (callGuard.inCall) return
        if (claimPending) { claimEvents++; return }
        claimPending = true
        claimWhy = why
        claimEvents = 1
        val wait = maxOf(CLAIM_DELAY_MS, lastClaimAt + CLAIM_MIN_GAP_MS - SystemClock.elapsedRealtime())
        NsLog.log("CLAIM", "$why: re-claim in $wait ms; last button to us ${keyAgo()}")
        main.postDelayed(claim, wait)
    }

    private fun runClaim() {
        claimPending = false
        val extra = if (claimEvents > 1) " (+${claimEvents - 1} more events)" else ""
        if (callGuard.inCall) { NsLog.log("CLAIM", "skipped: on a call"); return }
        if (!NavState.active && !Prefs.keepOnScreen) { NsLog.log("CLAIM", "skipped: setting turned off"); return }
        if (!NavState.active && !NsApp.watcher.otherIsPlaying()) {
            NsLog.log("CLAIM", "skipped: no other app is playing now (${claimWhy}$extra)")
            return
        }
        val now = SystemClock.elapsedRealtime()
        while (claimTimes.isNotEmpty() && now - claimTimes.first() > 30_000) claimTimes.removeFirst()
        if (claimTimes.size >= CLAIM_MAX_PER_30S) {
            NsLog.log("CLAIM", "limiter: ${claimTimes.size} claims in 30 s, something keeps taking it back; waiting 10 s")
            main.postDelayed({ claimSoon("retry after limiter") }, 10_000)
            return
        }
        claimTimes.addLast(now)
        lastClaimAt = now
        regrab("$claimWhy$extra", force = true)
        NsLog.log("CLAIM", "done: ${engine.current ?: "(nothing sent yet)"}; last button to us ${keyAgo()}")
    }

    /** The user asked (notification button): claim straight away. */
    fun claimNow(why: String) {
        if (callGuard.inCall) { NsLog.log("CLAIM", "$why: ignored, on a call"); return }
        NsLog.log("CLAIM", "$why: re-claiming now; last button to us ${keyAgo()}")
        lastClaimAt = SystemClock.elapsedRealtime()
        regrab(why, force = true)
    }

    /** "Keep Now Steering on screen" was switched. */
    fun onKeepChanged(on: Boolean) {
        NsLog.log("CLAIM", "keep on screen = $on")
        if (on) claimSoon("setting turned on")
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
        refreshScreen(force = true) // the latest screen; re-sending the last-sent one showed stale text for ~1 s
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
            zone.reset()
            dr.reset()
            tripMeters = null
            shownNav = null
            pendingNav = null
            main.removeCallbacks(settle)
            if (mode == Mode.IDLE_WHILE_NAV) mode = Mode.AUTO
            val last = lastNav
            lastNav = null
            if (arrived != null) {
                navEndedSinceArrival = true
                NsLog.log("SCREEN", "nav ended after arrival: arrived screen stays until the car moves")
                return
            }
            val left = last?.text?.remaining?.let { NavTextParser.metersOf(it) }
            if (left != null && left <= 150) {
                // Maps ends navigation by itself on arrival: say so instead of jumping straight to the clock.
                setArrived(last, "nav ended with ${last.text.remaining} left")
                navEndedSinceArrival = true
            } else {
                NsLog.log("SCREEN", "nav ended before arrival (${last?.text?.remaining ?: "?"} left): idle screen")
                showNotice(Screen("Nav ended", "", ""), 4000)
            }
            return
        }
        if (lastNav == null) {
            navStartAt = SystemClock.elapsedRealtime()
            if (navEndedSinceArrival) clearArrived("new navigation")
            powerSaveWarning("navigation started")
        }
        lastNav = info
        notice = null
        // Destination marker within 30 m: arrived now (Maps can take a minute to end navigation).
        val left = NavTextParser.metersOf(info.text.remaining)
        if (info.glyph == "●" && left != null && left <= 30) {
            if (arrived == null) setArrived(info, "destination marker with ${info.text.remaining} left")
            else if (info.text.road.isNotEmpty()) arrived = arrived!!.copy(artist = info.text.road)
            refreshScreen()
            return
        }
        // A new route flips between instructions for a few seconds; only show a change that holds for 3 s.
        val rerouting = NavTextParser.isRerouting(info.text.road)
        val settling = !rerouting && SystemClock.elapsedRealtime() - navStartAt < 30_000
        val established = shownNav?.text?.distance?.isNotEmpty() == true // not just "Starting navigation…"
        if (settling && established && info.instructionId != shownNav!!.instructionId) {
            if (pendingNav?.instructionId != info.instructionId) {
                main.removeCallbacks(settle)
                main.postDelayed(settle, 3000)
            }
            pendingNav = info
            return
        }
        pendingNav = null
        main.removeCallbacks(settle)
        apply(info)
    }

    private val settle = Runnable {
        pendingNav?.let { NsLog.log("NAV", "start-up: '${it.text.road}' held 3 s, showing it"); pendingNav = null; apply(it) }
    }

    private fun apply(info: NavState.Info) {
        shownNav = info
        NavTextParser.metersOf(info.text.remaining)?.let { m -> tripMeters = maxOf(tripMeters ?: 0.0, m) }
        dr.onMaps(info.instructionId, info.text.distance, NavTextParser.metersOf(info.text.distance))?.let { NsLog.log("DR", it) }
        if (info.instructionId != lastInstruction) {
            val first = lastInstruction == null
            lastInstruction = info.instructionId
            zone.reset()
            regrab(if (first) "navigation started" else "new instruction", force = first)
        }
        refreshScreen()
    }

    private fun setArrived(i: NavState.Info, why: String) {
        arrived = Screen("● Arrived", i.text.road, clockFmt.format(Date()))
        arrivedAt = SystemClock.elapsedRealtime()
        arrivedLoc = lastLoc
        navEndedSinceArrival = false
        stoppedSinceArrival = false
        NsLog.log("SCREEN", "arrived ($why): stays until the car moves, a new trip, double Next or disconnect")
        refreshScreen()
    }

    private fun clearArrived(why: String) {
        if (arrived == null) return
        arrived = null
        arrivedLoc = null
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
        val nav = if (NavState.active) shownNav else null
        val s = when {
            notice != null && SystemClock.elapsedRealtime() < noticeUntil -> notice!!
            mode == Mode.TEST -> testScreen ?: idleScreen()
            arrived != null -> arrived!!
            nav != null && mode == Mode.AUTO -> navScreen(nav)
            else -> idleScreen()
        }
        engine.show(s, force)
    }

    /**
     * Nav screen. Line 1: arrow + distance to the turn. Far from the turn: line 2 = time left + trip bar,
     * line 3 = the song that is playing (else the road). Last 300 m: line 2 = turn bar, line 3 = Maps' road text.
     */
    private fun navScreen(i: NavState.Info): Screen {
        val t = i.text
        // A real reroute: say so until Maps sends the new instruction.
        if (NavTextParser.isRerouting(t.road)) return Screen("• Rerouting...", "", "")
        val toTurn = dr.shown()
        val distance = toTurn?.let { NavTextParser.formatMeters(it) } ?: t.distance
        val title = "${i.glyph} $distance".trim()
        val near = zone.update(toTurn)
        val prefix = Prefs.songPrefix

        val line2 = if (near && Prefs.turnBar) {
            "${i.glyph} ${Bars.turnBar(minOf(toTurn!!, Bars.TURN_BAR_FROM_M))}"
        } else {
            Layouts.farLine2(t.timeLeft, t.remaining, t.eta, Bars.tripFraction(NavTextParser.metersOf(t.remaining), tripMeters),
                showArrival = Prefs.arrivalTime, showBar = Prefs.tripBar)
        }
        val song = if (near) null else currentSong()?.let { Layouts.songText(it.title, it.artist, prefix) }
        return Screen.lines(title, line2, song ?: t.road,
            line2Max = 20, line3Max = if (song != null) Glyphs.SONG_MAX else Glyphs.LINE_MAX, extra = prefix.extra)
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
        if (Prefs.keepOnScreen) mirrorScreen(title)?.let { return it }
        return Screen(title, dateFmt.format(now), "Now Steering")
    }

    /**
     * What is playing, held for 4 s when a music app briefly reports nothing between songs
     * (it sends empty titles mid-change), so the line does not flick to the road and back.
     */
    private fun currentSong(): SessionWatcher.Song? {
        val now = SystemClock.elapsedRealtime()
        NsApp.watcher.song()?.let { heldSong = it; heldSongAt = now; return it }
        return heldSong?.takeIf { now - heldSongAt < 4000 }
    }

    /** "Keep Now Steering on screen", not navigating: clock + speed, the playing title, and a time-left bar. */
    private fun mirrorScreen(title: String): Screen? {
        val song = currentSong() ?: return null
        if (Glyphs.sanitize(song.title, 40).count { it.isLetterOrDigit() } < 2) return null
        val artist = Glyphs.sanitize(song.artist, 40)
        return Screen.lines(title, "▶ ${song.title}", Layouts.mirrorLine3(song.durationMs, song.positionMs, artist),
            line2Max = Glyphs.SONG_MAX, line3Max = Glyphs.SONG_MAX)
    }

    private val ticker = object : Runnable {
        override fun run() {
            checkGpsStale()
            if (arrived != null && SystemClock.elapsedRealtime() - arrivedAt > 20 * 60_000) clearArrived("20 min since arrival")
            refreshScreen() // engine skips it unless the text changed
            main.postDelayed(this, 1000)
        }
    }

    // ---- GPS speed ----

    private val locListener = LocationListener { loc: Location ->
        if (loc.hasSpeed()) {
            val now = SystemClock.elapsedRealtime()
            // Only count distance between fixes that are close together (GPS gaps are not guessed over).
            if (now - speedAt <= 3000) dr.onSpeed(speedMps.toDouble(), now - speedAt)
            speedMps = loc.speed
            val k = (loc.speed * 3.6f).roundToInt()
            speedKmh = if (k < 2) 0 else k
            speedAt = now
            if (gpsStale) { gpsStale = false; NsLog.log("GPS", "fixes coming in") }
            if (arrived != null && k <= 5) stoppedSinceArrival = true
            if (arrived != null && stoppedSinceArrival && k > 10) clearArrived("moving again at $k km/h")
        }
        onAnyLocation(loc)
    }

    /** Network / passive fixes still arrive when Power saving blocks our GPS: used to notice driving away. */
    private val anyLocListener = LocationListener { loc: Location -> onAnyLocation(loc) }

    private fun onAnyLocation(loc: Location) {
        lastLoc = loc
        if (arrived == null) return
        val from = arrivedLoc
        if (from == null) { arrivedLoc = loc; return }
        val moved = from.distanceTo(loc)
        if (moved > 300) clearArrived("moved ${moved.roundToInt()} m from where we arrived (${loc.provider})")
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
            val lm = getSystemService(LocationManager::class.java)
            for (p in listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (p == LocationManager.PASSIVE_PROVIDER || lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 10_000L, 0f, anyLocListener, Looper.getMainLooper())
                }
            }
            locationOn = true
            NsLog.log("GPS", "speed updates on")
        } catch (e: Exception) {
            NsLog.log("GPS", "could not start: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun stopLocation() {
        if (locationOn) {
            getSystemService(LocationManager::class.java).removeUpdates(locListener)
            getSystemService(LocationManager::class.java).removeUpdates(anyLocListener)
        }
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
        if (what == "next" || what == "previous") {
            val d = ForwardGuard.check(t.playbackState?.state, NsApp.watcher.quietMs(t))
            if (!d.ok) {
                NsLog.log("FWD", "$what DROPPED, not sent to ${SessionWatcher.short(t.packageName)}: ${d.reason} (before: $before)")
                return
            }
        }
        action(t.transportControls)
        NsLog.log("FWD", "$what -> ${t.packageName} (before: $before)")
        if (what == "next" || what == "previous") claimSoon("forwarded $what")
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
            lastKeyAt = SystemClock.elapsedRealtime()
            val action = if (ke.action == KeyEvent.ACTION_DOWN) "DOWN" else if (ke.action == KeyEvent.ACTION_UP) "UP" else "action${ke.action}"
            NsLog.log("KEY", "${KeyEvent.keyCodeToString(ke.keyCode)} (${ke.keyCode}) $action repeat=${ke.repeatCount}")
            return buttons.onKey(ke) || super.onMediaButtonEvent(intent)
        }

        // Only reached for commands that arrive without a KeyEvent.
        override fun onSkipToNext() { lastKeyAt = SystemClock.elapsedRealtime(); NsLog.log("KEY", "onSkipToNext without KeyEvent"); buttons.nextTap() }
        override fun onSkipToPrevious() { lastKeyAt = SystemClock.elapsedRealtime(); NsLog.log("KEY", "onSkipToPrevious without KeyEvent"); buttons.previous() }
        override fun onPause() { lastKeyAt = SystemClock.elapsedRealtime(); NsLog.log("KEY", "onPause without KeyEvent"); buttons.playPause("PAUSE") }
        override fun onPlay() { lastKeyAt = SystemClock.elapsedRealtime(); NsLog.log("KEY", "onPlay without KeyEvent"); buttons.playPause("PLAY") }
        override fun onStop() { NsLog.log("KEY", "onStop without KeyEvent (ignored)") }
    }
}
