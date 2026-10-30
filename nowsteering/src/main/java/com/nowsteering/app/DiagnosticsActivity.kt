package com.nowsteering.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

/** The probe's tools: gesture readout, what the car is sent, Maps parse, sessions, text tester, event log. */
class DiagnosticsActivity : BaseActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var gestureView: TextView
    private lateinit var statusView: TextView
    private lateinit var navView: TextView
    private lateinit var sessionsView: TextView
    private lateinit var logView: TextView
    private val inputs = ArrayList<EditText>()

    private val logListener: () -> Unit = { renderLog() }
    private val ticker = object : Runnable {
        override fun run() { NsApp.watcher.refresh(); render(); main.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page()
        gestureView = big(0x332255CC)
        statusView = mono(12f)

        section("Maps parse")
        navView = mono(12f)

        section("Text tester (sent through the display engine)")
        listOf("Title (15)", "Artist (18)", "Album (18)").forEach { addField(it) }
        row(button("Show on car") {
            val s = SteeringService.instance ?: return@button toast("Service is off")
            s.showTest(Screen(inputs[0].text.toString(), inputs[1].text.toString(), inputs[2].text.toString()))
        }, button("Back to live") { SteeringService.instance?.backToLive() },
            button("Re-grab") { SteeringService.instance?.regrab("Diagnostics button", force = true) })

        section("Buttons")
        val tap = EditText(this).apply { setText(Prefs.tapWindowMs.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        row(TextView(this).apply { text = "Next tap window ms" }, tap, button("Apply") {
            Prefs.tapWindowMs = tap.text.toString().toIntOrNull()?.coerceIn(200, 2000) ?: 600
            tap.setText(Prefs.tapWindowMs.toString())
            NsLog.log("UI", "tap window = ${Prefs.tapWindowMs}ms")
        })

        section("Active media sessions")
        sessionsView = mono(12f)

        section("Event log")
        val note = EditText(this).apply { hint = "note, e.g. 'step 7'" }
        row(note, button("Add note") {
            val t = note.text.toString().trim()
            if (t.isNotEmpty()) { NsLog.log("NOTE", t); note.setText("") }
        })
        row(button("Share text") { shareText() }, button("Share file") { shareFile() },
            button("Copy") { copyLog() }, button("Clear") { confirmClear() })
        logView = mono(11f).apply { setTextIsSelectable(true) }
    }

    private fun addField(label: String) {
        val input = EditText(this).apply { hint = label; isSingleLine = true }
        inputs.add(input)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("preset…") + Presets.list.map { it.first })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos > 0) { input.setText(Presets.list[pos - 1].second); spinner.setSelection(0) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        row(input, spinner)
    }

    override fun onResume() {
        super.onResume()
        NsLog.addListener(logListener)
        renderLog()
        main.post(ticker)
    }

    override fun onPause() {
        NsLog.removeListener(logListener)
        main.removeCallbacks(ticker)
        super.onPause()
    }

    private fun render() {
        val s = SteeringService.instance
        gestureView.text = s?.gesture ?: "(service off)"
        statusView.text = buildString {
            append("service=").append(if (s == null) "OFF" else "ON")
            if (s != null) append(" mode=").append(s.mode).append(" steppedAside=").append(s.steppedAside)
            append('\n').append("car gets: ").append(s?.currentScreen?.toString() ?: "-")
        }
        val i = NavState.info
        navView.text = if (i == null) "not navigating" else
            "glyph=${i.glyph} via ${i.glyphSource}\ndistance='${i.text.distance}' road='${i.text.road}'\n" +
                "eta='${i.text.eta}' left='${i.text.remaining}'\nicon=${i.iconHex?.take(24) ?: "none"}…"
        sessionsView.text = NsApp.watcher.summary()
    }

    private fun renderLog() {
        logView.text = NsLog.recent().asReversed().take(200).joinToString("\n")
    }

    private fun fullLog() = NsApp.instance.header() + NsLog.fileText()

    private fun shareText() {
        var text = fullLog()
        val limit = 200_000
        if (text.length > limit) text = NsApp.instance.header() + "[truncated to the last $limit chars; use Share file]\n" + text.takeLast(limit)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Now Steering log")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share log"))
    }

    private fun shareFile() {
        LogFileProvider.shareFile(this).writeText(fullLog())
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Now Steering log")
            putExtra(Intent.EXTRA_STREAM, LogFileProvider.URI)
            clipData = ClipData.newRawUri("log", LogFileProvider.URI)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share log file"))
    }

    private fun copyLog() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Now Steering log", fullLog().takeLast(200_000)))
        toast("Log copied")
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(this)
            .setMessage("Clear the whole event log?")
            .setPositiveButton("Clear") { _, _ -> NsLog.clear(); NsLog.log("APP", "log cleared") }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
