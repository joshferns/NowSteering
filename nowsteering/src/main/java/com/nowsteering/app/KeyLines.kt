package com.nowsteering.app

/**
 * The lines "Share key lines" exports: buttons, gestures, forwards and drops, claims, session changes,
 * your notes, and the mirror screens. Everything else (Maps raw fields, GPS, per-second screens) is left out,
 * and a line identical to the one before it (apart from its timestamp) is dropped.
 */
object KeyLines {
    private val tags = Regex("""\[(KEY|GESTURE|FWD|CLAIM|SESSION|SESSIONS|UI|NOTE|CALL|TORQUE)\]""")

    fun filter(log: String): List<String> {
        val kept = ArrayList<String>()
        var lastBody = ""
        for (line in log.lineSequence()) {
            val isMirror = line.contains("[SCREEN]") && (line.contains("▶") || line.contains("♪"))
            if (!isMirror && !tags.containsMatchIn(line)) continue
            val body = line.substringAfter("] ", line)
            if (body == lastBody) continue
            lastBody = body
            kept.add(line)
        }
        return kept
    }
}
