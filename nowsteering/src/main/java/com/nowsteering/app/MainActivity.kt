package com.nowsteering.app

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CheckBox
import android.widget.TextView

/** Settings: pick the car, auto-start on/off, permissions, manual start/stop. */
class MainActivity : BaseActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var checklist: TextView
    private lateinit var status: TextView
    private lateinit var carView: TextView

    private val ticker = object : Runnable {
        override fun run() { render(); main.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page()
        section("Now Steering")
        checklist = big(0).apply { textSize = 17f }
        status = mono(13f)

        section("Car")
        carView = mono(14f)
        row(button("Choose car") { chooseCar() })
        root.addView(CheckBox(this).apply {
            text = "Start automatically when the car connects"
            isChecked = Prefs.autoStart
            setOnCheckedChangeListener { _, b ->
                Prefs.autoStart = b
                NsLog.log("UI", "auto-start = $b")
            }
        })
        row(button("Start now") { SteeringService.start(this, "Start tapped") },
            button("Stop") { SteeringService.stop(this, "Stop tapped") })

        section("Permissions (one-time)")
        row(button("1. Allow restricted settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, button("2. Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        row(button("3. Notifications, Bluetooth, GPS") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }, button("4. GPS: allow all the time") {
            if (!NsApp.instance.granted(Manifest.permission.ACCESS_FINE_LOCATION)) toast("Do step 3 first")
            else requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        })
        row(button("5. Battery: don't optimise") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })

        section("More")
        row(button("Diagnostics") { startActivity(Intent(this, DiagnosticsActivity::class.java)) })
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        perms.forEachIndexed { i, p -> NsLog.log("PERM", "${p.substringAfterLast('.')} granted=${results.getOrNull(i) == 0}") }
    }

    override fun onResume() {
        super.onResume()
        main.post(ticker)
    }

    override fun onPause() {
        main.removeCallbacks(ticker)
        super.onPause()
    }

    private fun render() {
        val app = NsApp.instance
        val checks = listOf(
            "Notification access: nav + wheel buttons (step 2)" to app.listenerEnabled(),
            "Car chosen" to (Prefs.carAddress != null),
            "Battery not optimised (step 5)" to app.batteryExempt(),
            "GPS all the time (step 4)" to app.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            "Notifications + Bluetooth (step 3)" to (app.granted(Manifest.permission.POST_NOTIFICATIONS) && app.granted(Manifest.permission.BLUETOOTH_CONNECT)),
        )
        val missing = checks.count { !it.second }
        checklist.text = (if (missing == 0) "Setup complete\n" else "SETUP INCOMPLETE: $missing missing\n") +
            checks.joinToString("\n") { (name, ok) -> (if (ok) "OK   " else "MISSING  ") + name }
        checklist.setBackgroundColor(if (missing == 0) 0x3322AA22 else 0x55DD2222)
        val s = SteeringService.instance
        status.text = buildString {
            append("Service: ").append(if (s == null) "OFF" else if (s.steppedAside) "ON (stepped aside: call)" else "ON").append('\n')
            append("Navigating: ").append(if (NavState.active) "yes" else "no").append('\n')
            append("Car shows: ").append(s?.currentScreen?.toString() ?: "-").append('\n')
            append(NsApp.instance.permSummary().replace(" ", "\n"))
        }
        carView.text = "Car: ${Prefs.carName ?: "not chosen"} ${Prefs.carAddress ?: ""}"
    }

    private fun chooseCar() {
        if (!NsApp.instance.granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            toast("Allow Bluetooth first (permissions step 3)")
            return
        }
        val devices = try {
            getSystemService(BluetoothManager::class.java).adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) { emptyList() }
        if (devices.isEmpty()) { toast("No paired Bluetooth devices"); return }
        fun nameOf(d: android.bluetooth.BluetoothDevice) = try { d.name } catch (e: SecurityException) { null } ?: "?"
        val names = devices.map { "${nameOf(it)} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Which one is the car?")
            .setItems(names) { _, i ->
                Prefs.carAddress = devices[i].address
                Prefs.carName = nameOf(devices[i])
                NsLog.log("UI", "car chosen: ${names[i]}")
                render()
            }
            .show()
    }
}
