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
    private companion object {
        /** Text over this goes as a file: the chooser carries the text twice and Android's call limit is about 1 MB. */
        const val MAX_SHARE_TEXT = 60_000
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var gestureView: TextView
    private lateinit var statusView: TextView
    private lateinit var navView: TextView
    private lateinit var sessionsView: TextView
    private lateinit var torqueView: TextView
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
        note("Tester limits are the car's measured widths: 17 / 20 / 20, and the candidate glyphs ♪ ♫ ♩ ♬ … are let through so they can be checked.")
        // Top to bottom on the car: Title (big line), Album (disc icon), Artist (person icon).
        listOf("Title (big line, 17)", "Album (disc icon, 20)", "Artist (person icon, 20)").forEach { addField(it) }
        row(button("Show on car") {
            val s = SteeringService.instance ?: return@button toast("Service is off")
            s.showTest(Screen(inputs[0].text.toString(), artist = inputs[2].text.toString(), album = inputs[1].text.toString(),
                titleMax = 17, artistMax = 20, albumMax = 20, extra = Glyphs.TEST_EXTRA))
        }, button("Back to live") { SteeringService.instance?.backToLive() },
            button("Re-grab") { SteeringService.instance?.regrab("Diagnostics button", force = true) })

        section("Car test screens (one tap each)")
        Presets.testScreens.chunked(2).forEach { pair ->
            row(*pair.map { (name, screen) ->
                button(name) {
                    val svc = SteeringService.instance ?: return@button toast("Service is off")
                    svc.showTest(screen)
                }
            }.toTypedArray())
        }
        row(button("Back to live") { SteeringService.instance?.backToLive() })

        section("Song on the nav screen")
        note("Glyph before the song name. ▶ is proven on the car; ♪ and ♫ show as dots.")
        val prefixGroup = android.widget.RadioGroup(this)
        SongPrefix.entries.forEach { sp ->
            prefixGroup.addView(android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = sp.label
                isChecked = sp == Prefs.songPrefix
                setOnCheckedChangeListener { _, b ->
                    if (b && sp != Prefs.songPrefix) { Prefs.songPrefix = sp; NsLog.log("UI", "song prefix = ${sp.name}") }
                }
            })
        }
        root.addView(prefixGroup)

        section("Car refresh (phone-locked test)")
        root.addView(android.widget.CheckBox(this).apply {
            text = "Seconds on idle clock"
            isChecked = Prefs.idleSeconds
            setOnCheckedChangeListener { _, b -> Prefs.idleSeconds = b; NsLog.log("UI", "idle seconds = $b") }
        })
        val styles = android.widget.RadioGroup(this)
        SteeringService.RefreshStyle.entries.forEach { st ->
            styles.addView(android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = st.label
                isChecked = st.name == Prefs.refreshStyle
                setOnCheckedChangeListener { _, b ->
                    if (b) { Prefs.refreshStyle = st.name; NsLog.log("UI", "refresh style = ${st.name}") }
                }
            })
        }
        root.addView(styles)

        section("Torque (read-only probe; nothing here changes the car screen)")
        torqueView = mono(12f)
        row(button("Refresh PID list") { NsApp.torque.refreshPidList() })

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
        row(button("Share key lines") { shareKeyLines() })
        row(button("Share text") { shareText() }, button("Share file") { shareFile() },
            button("Copy") { copyLog() }, button("Clear") { confirmClear() })
        logView = mono(11f).apply { setTextIsSelectable(true) }
    }

    private fun addField(label: String) {
        val input = EditText(this).apply { hint = label; isSingleLine = true }
        inputs.add(input)
        val spinner = Spinner(this)
        // Closed view = simple_spinner_item; only the open list uses the dropdown-row layout.
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("preset…") + Presets.list.map { it.first })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos > 0) { input.setText(Presets.list[pos - 1].second); spinner.setSelection(0) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(input)    // full width, so the hint is not cut in half
        root.addView(spinner)  // full width below it
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
        torqueView.text = torqueText()
    }

    private fun torqueText(): String {
        val t = NsApp.torque.snapshot
        val now = System.currentTimeMillis()
        return buildString {
            append("Torque installed: ").append(when (t.install) {
                TorqueClient.Install.PRO -> "yes (Pro)"
                TorqueClient.Install.LITE -> "Lite (plugin API may not work)"
                TorqueClient.Install.NONE -> "no"
            }).append('\n')
            append("Plugin service bound: ").append(if (t.bound) "yes" else "no").append('\n')
            append("Torque version: ").append(t.version).append("  full permissions: ").append(t.fullPermissions).append('\n')
            append("OBD connected: ").append(when (t.obdConnected) { true -> "yes"; false -> "no"; null -> "unknown" }).append('\n')
            if (t.note.isNotEmpty()) append("Status: ").append(t.note).append('\n')
            if (t.pids.isNotEmpty()) {
                append("\nPIDs (").append(t.pids.size).append(" of ").append(t.allCount).append(" known; A = active in Torque, E = ECU supports)\n")
                t.pids.forEach { p ->
                    val r = t.rows[p.id]
                    append(if (p.active) 'A' else '-').append(if (p.ecuSupported) 'E' else '-').append(' ')
                    append(p.shortName.ifEmpty { p.name }).append(": ")
                    append(TorquePid.valueText(r?.first, r?.second, now)).append(' ').append(p.unit).append('\n')
                }
            }
        }
    }

    private fun renderLog() {
        logView.text = NsLog.recent().asReversed().take(200).joinToString("\n")
    }

    private fun fullLog() = NsApp.instance.header() + NsLog.fileText()

    /**
     * Only the lines that show who held the car and whether the buttons reached us (see [KeyLines]).
     * Sent as a file: a text extra over about 100 KB overflows Android's share call and fails silently.
     */
    private fun shareKeyLines() {
        val kept = KeyLines.filter(NsLog.fileText())
        val text = NsApp.instance.header() + "[key lines only: ${kept.size}]\n" + kept.joinToString("\n") + "\n"
        shareAsFile(LogFileProvider.KEYLINES_NAME, text, "key lines")
    }

    private fun shareText() {
        val text = fullLog()
        if (text.length > MAX_SHARE_TEXT) {
            toast("Log is large: sending it as a file")
            shareAsFile(LogFileProvider.FILE_NAME, text, "log")
            return
        }
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Now Steering log")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share log"))
        } catch (t: Throwable) {
            shareFailed("log text", t)
        }
    }

    private fun shareFile() = shareAsFile(LogFileProvider.FILE_NAME, fullLog(), "log")

    private fun shareAsFile(fileName: String, text: String, what: String) {
        try {
            val f = LogFileProvider.file(this, fileName)
            f.writeText(text)
            val uri = LogFileProvider.uri(fileName)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Now Steering $what")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(what, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share $what"))
            NsLog.log("UI", "sharing $what as a file: ${f.length() / 1024} KB")
        } catch (t: Throwable) {
            shareFailed(what, t)
        }
    }

    /** Sharing must never fail silently: log it and say so on screen. */
    private fun shareFailed(what: String, t: Throwable) {
        NsLog.log("UI", "sharing $what FAILED: ${t.javaClass.simpleName} ${t.message}")
        android.app.AlertDialog.Builder(this)
            .setTitle("Sharing failed")
            .setMessage("Could not share the $what:\n${t.javaClass.simpleName}: ${t.message}\n\nTry Share file instead.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun copyLog() {
        try {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Now Steering log", fullLog().takeLast(MAX_SHARE_TEXT)))
            toast("Copied the last ${MAX_SHARE_TEXT / 1000} KB of the log")
        } catch (t: Throwable) {
            shareFailed("copy", t)
        }
    }

    private fun confirmClear() {
        android.app.AlertDialog.Builder(this)
            .setMessage("Clear the whole event log?")
            .setPositiveButton("Clear") { _, _ -> NsLog.clear(); NsLog.log("APP", "log cleared") }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
