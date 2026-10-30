package com.nowsteering.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** Timestamped event log, mirrored to a file that survives restarts. Trimmed to the newest ~1 MB at 2 MB. */
object NsLog {
    private const val MAX_IN_MEMORY = 400
    private const val MAX_FILE_BYTES = 2_000_000L
    private const val KEEP_BYTES = 1_000_000
    private lateinit var file: File
    private val recentLines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "ns_log.txt")
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
        android.util.Log.i("NowSteering", line)
        io.execute {
            file.appendText(line + "\n")
            if (file.length() > MAX_FILE_BYTES) {
                val text = file.readText()
                file.writeText("[log trimmed to newest part]\n" + text.takeLast(KEEP_BYTES).substringAfter('\n'))
            }
        }
        main.post { listeners.forEach { it() } }
    }

    fun recent(): List<String> = synchronized(this) { recentLines.toList() }

    fun fileText(): String = io.submit<String> { if (file.exists()) file.readText() else "" }.get()

    fun clear() {
        synchronized(this) { recentLines.clear() }
        io.submit { file.writeText("") }.get()
        main.post { listeners.forEach { it() } }
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)
}
