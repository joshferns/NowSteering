package com.nowsteering.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

class NsApp : Application() {
    companion object {
        lateinit var instance: NsApp
        lateinit var watcher: SessionWatcher
    }

    private var lastPlayers = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
        NsLog.init(this)
        Prefs.init(this)
        NsLog.log("APP", "=== process start === ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})")
        watcher = SessionWatcher(this)
        watcher.start()
        val am = getSystemService(AudioManager::class.java)
        am.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                val s = configs.groupingBy { usage(it.audioAttributes.usage) }.eachCount()
                    .entries.joinToString { "${it.key}x${it.value}" }.ifEmpty { "none" }
                if (s != lastPlayers) {
                    lastPlayers = s
                    NsLog.log("AUDIO", "active players: $s")
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun usage(u: Int) = when (u) {
        AudioAttributes.USAGE_MEDIA -> "media"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "nav-voice"
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "ringtone"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "call"
        AudioAttributes.USAGE_NOTIFICATION -> "notif"
        else -> "usage$u"
    }

    fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun listenerEnabled() =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true

    fun batteryExempt() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    fun permSummary() = "listener=${listenerEnabled()} battery=${batteryExempt()} " +
        "notif=${granted(Manifest.permission.POST_NOTIFICATIONS)} bt=${granted(Manifest.permission.BLUETOOTH_CONNECT)} " +
        "gps=${granted(Manifest.permission.ACCESS_FINE_LOCATION)} gpsBackground=${granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)}"

    fun header() = buildString {
        appendLine("==== Now Steering log ====")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
        appendLine("App: ${BuildConfig.VERSION_NAME} code ${BuildConfig.VERSION_CODE} git ${BuildConfig.GIT_SHA} built ${BuildConfig.BUILD_TIME}")
        appendLine("Perms: ${permSummary()}")
        appendLine("Car: ${Prefs.carName} ${Prefs.carAddress} autoStart=${Prefs.autoStart} tapWindow=${Prefs.tapWindowMs}ms")
        appendLine("Service running: ${SteeringService.instance != null}, navigating: ${NavState.active}, known icons: ${KnownIcons.list.size}, seen icons: ${Prefs.seenIcons.size}")
        appendLine("==========================")
    }
}
