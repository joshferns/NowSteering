package com.nowsteering.probe

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** Timestamped event log, mirrored to a file in app storage so it survives restarts. */
object ProbeLog {
    private const val MAX_IN_MEMORY = 400
    private lateinit var file: File
    private val recentLines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "probe_log.txt")
        val tail = if (file.exists()) file.readLines().takeLast(MAX_IN_MEMORY) else emptyList()
        synchronized(this) { recentLines.addAll(tail) }
    }

    fun log(tag: String, msg: String) {
        val line = synchronized(this) {
            val l = "${fmt.format(Date())} [$tag] $msg"
            recentLines.addLast(l)
            while (recentLines.size > MAX_IN_MEMORY) recentLines.removeFirst()
            l
        }
        android.util.Log.i("NSProbe", line)
        io.execute { file.appendText(line + "\n") }
        main.post { listeners.forEach { it() } }
    }

    fun recent(): List<String> = synchronized(this) { recentLines.toList() }

    /** Whole log file (waits for pending writes). */
    fun fileText(): String = io.submit<String> { if (file.exists()) file.readText() else "" }.get()

    fun lineCount(): Int = io.submit<Int> { if (file.exists()) file.useLines { it.count() } else 0 }.get()

    fun clear() {
        synchronized(this) { recentLines.clear() }
        io.submit { file.writeText("") }.get()
        main.post { listeners.forEach { it() } }
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)
}
