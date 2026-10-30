package com.nowsteering.probe

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

class ProbeApp : Application() {
    companion object {
        lateinit var instance: ProbeApp
        lateinit var watcher: SessionWatcher
    }

    private val main = Handler(Looper.getMainLooper())
    private var lastPlaybackSummary = ""

    override fun onCreate() {
        super.onCreate()
        instance = this
        ProbeLog.init(this)
        ProbeLog.log("APP", "=== process start === ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA}) on ${Build.MODEL} Android ${Build.VERSION.RELEASE}")
        watcher = SessionWatcher(this)
        watcher.start()
        registerBluetooth()
        registerAudioPlayback()
    }

    // ---- Bluetooth ---------------------------------------------------------

    private fun hasBtPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun describe(d: BluetoothDevice?): String {
        if (d == null) return "(null device)"
        val name = try { d.name } catch (e: SecurityException) { "(name needs Bluetooth permission)" }
        val cls = try { d.bluetoothClass?.deviceClass?.let { "0x" + Integer.toHexString(it) } } catch (e: SecurityException) { null }
        return "'$name' ${d.address}" + (cls?.let { " class=$it" } ?: "")
    }

    private fun profileState(s: Int) = when (s) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        BluetoothA2dp.STATE_PLAYING -> "PLAYING"
        BluetoothA2dp.STATE_NOT_PLAYING -> "NOT_PLAYING"
        else -> "state$s"
    }

    @Suppress("DEPRECATION")
    private fun deviceExtra(i: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val dev = describe(deviceExtra(i))
            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> ProbeLog.log("BT", "ACL connected $dev")
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> ProbeLog.log("BT", "ACL disconnected $dev")
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> ProbeLog.log("BT",
                    "A2DP ${profileState(i.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1))} -> " +
                        "${profileState(i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1))} $dev")
                BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> ProbeLog.log("BT",
                    "A2DP audio ${profileState(i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1))} $dev")
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> ProbeLog.log("AUDIO", "becoming noisy (audio route lost)")
            }
        }
    }

    private fun registerBluetooth() {
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(btReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(btReceiver, f)
        logConnectedA2dp()
    }

    /** Logs the A2DP devices connected right now (broadcasts only tell us about changes). */
    fun logConnectedA2dp() {
        if (!hasBtPermission()) {
            ProbeLog.log("BT", "Bluetooth permission not granted: device names unavailable")
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        try {
            adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        val devs = proxy.connectedDevices
                        ProbeLog.log("BT", "A2DP connected now: " + if (devs.isEmpty()) "none" else devs.joinToString { describe(it) })
                    } catch (e: SecurityException) {
                        ProbeLog.log("BT", "A2DP query denied: ${e.message}")
                    }
                    adapter.closeProfileProxy(profile, proxy)
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        } catch (e: SecurityException) {
            ProbeLog.log("BT", "A2DP query denied: ${e.message}")
        }
    }

    // ---- Audio players -----------------------------------------------------

    private fun usage(u: Int) = when (u) {
        AudioAttributes.USAGE_MEDIA -> "media"
        AudioAttributes.USAGE_GAME -> "game"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "nav"
        AudioAttributes.USAGE_NOTIFICATION -> "notif"
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> "voice"
        else -> "usage$u"
    }

    private fun registerAudioPlayback() {
        val am = getSystemService(AudioManager::class.java)
        am.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                // Android only reports *active* players to normal apps; package names are hidden.
                val s = configs.groupingBy { usage(it.audioAttributes.usage) }.eachCount()
                    .entries.joinToString { "${it.key}x${it.value}" }.ifEmpty { "none" }
                if (s != lastPlaybackSummary) {
                    lastPlaybackSummary = s
                    ProbeLog.log("AUDIO", "active players: $s (music on=${am.isMusicActive})")
                }
            }
        }, main)
    }

    // ---- Status ------------------------------------------------------------

    fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    fun batteryExempt() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    fun notifPermission() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun header(): String {
        val oneUi = try { Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null).toString() } catch (e: Exception) { "n/a" }
        return buildString {
            appendLine("==== Now Steering Phase 0 probe log ====")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}, build ${Build.DISPLAY}, SEM_PLATFORM_INT=$oneUi")
            appendLine("App: ${BuildConfig.VERSION_NAME} code ${BuildConfig.VERSION_CODE} git ${BuildConfig.GIT_SHA} built ${BuildConfig.BUILD_TIME}")
            appendLine("Perms: notif=${notifPermission()} bt=${hasBtPermission()} listener=${listenerEnabled()} batteryExempt=${batteryExempt()}")
            appendLine("Settings: mode=${ProbeState.mode} focus=${ProbeState.requestAudioFocus} mirror=${ProbeState.mirrorTarget} forward=${ProbeState.forwardToTarget} tapWindow=${ProbeState.tapWindowMs}ms hold=${ProbeState.holdThresholdMs}ms")
            appendLine("Session running: ${ProbeService.instance != null}")
            appendLine("========================================")
        }
    }
}
