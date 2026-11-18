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
    private lateinit var presetView: TextView

    private fun trimNumber(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /** What the selected preset will show on the car, cut the way the car cuts it. */
    private fun renderPreview() {
        val s = com.nowsteering.app.Settings.fromPrefs()
        presetView.text = "What the car will show (sample values)\n\n" + PresetPreview.format(PresetPreview.render(s))
    }

    private val ticker = object : Runnable {
        override fun run() { render(); main.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page()
        section("Now Steering")
        checklist = big(0).apply { textSize = 17f }
        status = mono(13f)

        section("Screen preset")
        val torque = NsApp.torque.snapshot.install != TorqueClient.Install.NONE
        presetView = mono(12f)
        val group = android.widget.RadioGroup(this)
        PresetId.available(torque).forEach { id ->
            group.addView(android.widget.RadioButton(this).apply {
                this.id = android.view.View.generateViewId()
                text = "${id.label}: ${id.blurb}"
                isChecked = id == Prefs.preset
                setOnCheckedChangeListener { _, b ->
                    if (b && id != Prefs.preset) { Prefs.preset = id; NsLog.log("UI", "preset = ${id.label}"); renderPreview() }
                }
            })
        }
        // Put the radio group above its preview.
        root.removeView(presetView)
        root.addView(group)
        root.addView(presetView)
        renderPreview()

        section("Trip cost")
        note("Fuel price turns on the cost lines of the Trip preset. Typical km/L is only for when Torque has no figure.")
        val price = android.widget.EditText(this).apply {
            hint = "Fuel price, Rs per litre (empty = no cost)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(Prefs.fuelPrice?.let { trimNumber(it) } ?: "")
        }
        val typical = android.widget.EditText(this).apply {
            hint = "Typical km/L (optional)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(Prefs.typicalKmpl?.let { trimNumber(it) } ?: "")
        }
        root.addView(price); root.addView(typical)
        row(button("Save cost settings") {
            Prefs.fuelPrice = price.text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
            Prefs.typicalKmpl = typical.text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
            price.setText(Prefs.fuelPrice?.let { trimNumber(it) } ?: ""); typical.setText(Prefs.typicalKmpl?.let { trimNumber(it) } ?: "")
            NsLog.log("UI", "fuel price = ${Prefs.fuelPrice ?: "not set"}, typical km/L = ${Prefs.typicalKmpl ?: "not set"}")
            renderPreview()
            toast("Saved")
        })
        if (torque) {
            root.addView(CheckBox(this).apply {
                text = "Engine warnings from Torque (coolant high, voltage low)"
                isChecked = Prefs.engineWarnings
                setOnCheckedChangeListener { _, b -> Prefs.engineWarnings = b; NsLog.log("UI", "engine warnings = $b") }
            })
        }

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
        root.addView(CheckBox(this).apply {
            text = "Classic preset: turn bar (last 300 m before a turn)"
            isChecked = Prefs.turnBar
            setOnCheckedChangeListener { _, b -> Prefs.turnBar = b; NsLog.log("UI", "turn bar = $b") }
        })
        root.addView(CheckBox(this).apply {
            text = "Classic preset: trip bar (after the ETA)"
            isChecked = Prefs.tripBar
            setOnCheckedChangeListener { _, b -> Prefs.tripBar = b; NsLog.log("UI", "trip bar = $b") }
        })
        root.addView(CheckBox(this).apply {
            text = "Classic preset: arrival time on line 2 (replaces the trip bar)"
            isChecked = Prefs.arrivalTime
            setOnCheckedChangeListener { _, b -> Prefs.arrivalTime = b; NsLog.log("UI", "arrival time = $b") }
        })
        root.addView(CheckBox(this).apply {
            text = "Keep Now Steering on screen (take the car back when another app plays)"
            isChecked = Prefs.keepOnScreen
            setOnCheckedChangeListener { _, b ->
                Prefs.keepOnScreen = b
                NsLog.log("UI", "keep on screen = $b")
                SteeringService.instance?.onKeepChanged(b)
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
            "Power saving OFF (it blocks GPS when locked)" to !app.powerSaveOn(),
            "GPS all the time (step 4)" to app.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            "Notifications + Bluetooth (step 3)" to (app.granted(Manifest.permission.POST_NOTIFICATIONS) && app.granted(Manifest.permission.BLUETOOTH_CONNECT)),
        )
        val missing = checks.count { !it.second }
        checklist.text = (if (missing == 0) "Setup complete\n" else "SETUP INCOMPLETE: $missing missing\n") +
            checks.joinToString("\n") { (name, ok) -> (if (ok) "OK   " else "MISSING  ") + name }
        checklist.append("\nINFO  Google Maps battery: " + if (app.mapsBatteryExempt()) "not optimised" else "optimised")
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
