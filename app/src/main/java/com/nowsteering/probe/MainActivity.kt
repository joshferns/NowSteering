package com.nowsteering.probe

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var flashView: TextView
    private lateinit var statusView: TextView
    private lateinit var sessionsView: TextView
    private lateinit var logView: TextView
    private val fieldInputs = HashMap<String, EditText>()

    private val logListener: () -> Unit = { renderLog(); renderFlash() }
    private val ticker = object : Runnable {
        override fun run() {
            ProbeApp.watcher.refresh()
            renderStatus()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        perms.forEachIndexed { i, p ->
            ProbeLog.log("PERM", "${p.substringAfterLast('.')} granted=${results.getOrNull(i) == 0}")
        }
        ProbeApp.instance.logConnectedA2dp()
    }

    override fun onResume() {
        super.onResume()
        ProbeLog.addListener(logListener)
        renderLog(); renderFlash()
        main.post(ticker)
    }

    override fun onPause() {
        ProbeLog.removeListener(logListener)
        main.removeCallbacks(ticker)
        super.onPause()
    }

    // ---- UI building helpers -----------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun section(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
    }.also { root.addView(it) }

    private fun row(vararg views: View) {
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
        })
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun check(label: String, initial: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label
        isChecked = initial
        setOnCheckedChangeListener { _, b -> onChange(b) }
    }.also { root.addView(it) }

    private fun service(): ProbeService? = ProbeService.instance.also {
        if (it == null) toast("Start the session first")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })

        flashView = TextView(this).apply {
            textSize = 30f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0x3322AA22)
        }
        root.addView(flashView)
        statusView = TextView(this).apply { textSize = 12f; typeface = Typeface.MONOSPACE }
        root.addView(statusView)

        // Setup
        section("0. Setup")
        row(button("Notif listener access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            button("Battery: don't optimise") { openBatteryExemption() })
        row(button("App info (restricted settings)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, button("Ask permissions again") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT), 1)
        })

        // Session
        section("1. Media session (silent audio)")
        check("Request audio focus on start (untick for coexistence: keeps YT Music playing)", ProbeState.requestAudioFocus) {
            ProbeState.requestAudioFocus = it
            ProbeLog.log("UI", "request audio focus = $it")
        }
        row(button("START session") {
            ProbeLog.log("UI", "start session tapped")
            startForegroundService(Intent(this, ProbeService::class.java))
        }, button("STOP session") {
            ProbeLog.log("UI", "stop session tapped")
            stopService(Intent(this, ProbeService::class.java))
        }, button("Re-grab") { service()?.regrab() })

        // Refresh mode
        section("2. Refresh mode (used by every send)")
        val group = RadioGroup(this)
        ProbeState.RefreshMode.entries.forEach { m ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = m.label
                isChecked = m == ProbeState.mode
                setOnCheckedChangeListener { _, b ->
                    if (b) { ProbeState.mode = m; ProbeLog.log("UI", "refresh mode = ${m.name}") }
                }
            })
        }
        root.addView(group)

        // Text tester
        section("3. Text tester")
        addField("Title", { ProbeState.title }) { ProbeState.title = it }
        addField("Artist", { ProbeState.artist }) { ProbeState.artist = it }
        addField("Album", { ProbeState.album }) { ProbeState.album = it }
        row(button("Send ALL three fields") {
            ProbeState.title = fieldInputs["Title"]!!.text.toString()
            ProbeState.artist = fieldInputs["Artist"]!!.text.toString()
            ProbeState.album = fieldInputs["Album"]!!.text.toString()
            service()?.pushMetadata("send all")
        })

        // Auto counter
        section("4. Auto counter (title = #n HH:mm:ss)")
        val secs = EditText(this).apply {
            setText("5")
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "seconds"
        }
        row(secs, button("Start auto") {
            val n = secs.text.toString().toIntOrNull()?.coerceIn(1, 600) ?: 5
            service()?.startAuto(n)
        }, button("Stop auto") { service()?.stopAuto() })

        // Coexistence
        section("5. Coexistence with YouTube Music")
        check("Mirror target's title/artist/album into our session", ProbeState.mirrorTarget) {
            ProbeState.mirrorTarget = it
            ProbeLog.log("UI", "mirror = $it")
            if (it) service()?.mirror(ProbeApp.watcher.target()?.metadata)
        }
        check("Forward next/prev/play/pause we receive to target", ProbeState.forwardToTarget) {
            ProbeState.forwardToTarget = it
            ProbeLog.log("UI", "forward = $it")
        }
        row(button("Log session list now") { ProbeLog.log("SESSIONS", "snapshot:\n" + ProbeApp.watcher.summary()) },
            button("Target: next") {
                ProbeApp.watcher.target()?.transportControls?.skipToNext()
                ProbeLog.log("UI", "manual next -> ${ProbeApp.watcher.target()?.packageName}")
            })
        note("Active sessions (live, first = what Android ranks top):")
        sessionsView = TextView(this).apply { textSize = 12f; typeface = Typeface.MONOSPACE }
        root.addView(sessionsView)

        // Log
        section("6. Event log")
        val noteInput = EditText(this).apply { hint = "note, e.g. 'step 7: car shows #3'" }
        row(noteInput, button("Add note") {
            val t = noteInput.text.toString().trim()
            if (t.isNotEmpty()) { ProbeLog.log("NOTE", t); noteInput.setText("") }
        })
        row(button("Share text") { shareText() }, button("Share file") { shareFile() },
            button("Copy") { copyLog() }, button("Clear") { confirmClear() })
        logView = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(logView)
    }

    private fun addField(name: String, get: () -> String, set: (String) -> Unit) {
        note(name)
        val input = EditText(this).apply { setText(get()); isSingleLine = true }
        fieldInputs[name] = input
        val spinner = Spinner(this)
        val labels = listOf("preset…") + Presets.list.map { it.first }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos > 0) { input.setText(Presets.list[pos - 1].second); spinner.setSelection(0) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(input)
        row(spinner, button("Send $name") {
            set(input.text.toString())
            service()?.pushMetadata("send $name")
        })
    }

    // ---- Rendering ---------------------------------------------------------

    private fun renderFlash() {
        flashView.text = ProbeState.flash
    }

    private fun renderStatus() {
        val app = ProbeApp.instance
        val s = ProbeService.instance
        statusView.text = buildString {
            append("session=").append(if (s != null) "RUNNING" else "stopped")
            if (s?.autoRunning == true) append(" auto#").append(s.autoCount)
            append("  mode=").append(ProbeState.mode.name).append('\n')
            append("listener=").append(app.listenerEnabled())
            append(" battExempt=").append(app.batteryExempt())
            append(" notif=").append(app.notifPermission()).append('\n')
            append("sending: '").append(ProbeState.title).append("' / '").append(ProbeState.artist)
                .append("' / '").append(ProbeState.album).append("'")
        }
        sessionsView.text = ProbeApp.watcher.summary()
    }

    private fun renderLog() {
        logView.text = ProbeLog.recent().asReversed().take(200).joinToString("\n")
    }

    // ---- Actions -----------------------------------------------------------

    private fun openBatteryExemption() {
        ProbeLog.log("UI", "battery exemption screen opened (currently exempt=${ProbeApp.instance.batteryExempt()})")
        try {
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun fullLog() = ProbeApp.instance.header() + ProbeLog.fileText()

    private fun shareText() {
        var text = fullLog()
        val limit = 200_000 // keep under the Binder transaction limit
        if (text.length > limit) {
            text = ProbeApp.instance.header() + "[log truncated to the last $limit chars; use Share file for all]\n" +
                text.takeLast(limit)
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NS probe log")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share log"))
    }

    private fun shareFile() {
        LogFileProvider.shareFile(this).writeText(fullLog())
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NS probe log")
            putExtra(Intent.EXTRA_STREAM, LogFileProvider.URI)
            clipData = ClipData.newRawUri("log", LogFileProvider.URI)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share log file"))
    }

    private fun copyLog() {
        val text = fullLog().takeLast(200_000)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("NS probe log", text))
        toast("Log copied (${ProbeLog.lineCount()} lines)")
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(this)
            .setMessage("Clear the whole event log?")
            .setPositiveButton("Clear") { _, _ -> ProbeLog.clear(); ProbeLog.log("APP", "log cleared") }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
