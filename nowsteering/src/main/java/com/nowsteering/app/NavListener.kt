package com.nowsteering.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads Google Maps' navigation notification. Also grants us access to other apps' media sessions. */
class NavListener : NotificationListenerService() {
    companion object {
        const val MAPS = "com.google.android.apps.maps"
    }

    private val lastRaw = HashMap<String, String>()

    override fun onListenerConnected() {
        NsLog.log("LISTENER", "notification listener connected")
        NsApp.watcher.start()
        try {
            activeNotifications?.filter { it.packageName == MAPS }?.forEach { onMaps(it) }
        } catch (e: Exception) {
            NsLog.log("LISTENER", "could not read current notifications: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        NsLog.log("LISTENER", "notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS) onMaps(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS) return
        lastRaw.remove(sbn.key)
        NsLog.log("MAPS", "notification removed key=${sbn.key}")
        if (sbn.key == NavState.key) NavState.end("Maps notification removed")
    }

    private fun onMaps(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            NsLog.log("MAPS", "parse error ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification
        val ex = n.extras
        fun s(k: String) = ex.getCharSequence(k)?.toString()
        val title = s(Notification.EXTRA_TITLE)
        val text = s(Notification.EXTRA_TEXT)
        val sub = s(Notification.EXTRA_SUB_TEXT)
        val big = s(Notification.EXTRA_BIG_TEXT)
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isNav = n.category == Notification.CATEGORY_NAVIGATION ||
            (ongoing && (sub?.contains("ETA", true) == true || title?.let { Regex("""\d\s?(m|km|mi|ft|yd)\b""").containsMatchIn(it) } == true))

        // Raw fields, logged whenever they change, so the parser can be tuned from real drives.
        val raw = "key=${sbn.key} cat=${n.category} ch=${n.channelId} ongoing=$ongoing nav=$isNav " +
            "title='$title' text='$text' sub='$sub' big='$big' info='${s(Notification.EXTRA_INFO_TEXT)}' " +
            "summary='${s(Notification.EXTRA_SUMMARY_TEXT)}' bigTitle='${s(Notification.EXTRA_TITLE_BIG)}' " +
            "actions=[${n.actions?.joinToString { it.title.toString() } ?: ""}] largeIcon=${n.getLargeIcon() != null} " +
            "customView=${n.contentView != null || n.bigContentView != null}"
        if (lastRaw[sbn.key] != raw) {
            lastRaw[sbn.key] = raw
            NsLog.log("MAPS", "raw $raw")
        }
        if (!isNav) return

        val heading = NavTextParser.isHeadingVariant(title, text)
        // The variant comes as "0 m" or "" + "towards X"; show both the same way so the car doesn't flicker.
        val parsed = NavTextParser.parse(title, text, sub, big).let { if (heading) it.copy(distance = "") else it }
        val mask = IconSig.mask(this, n.getLargeIcon())
        val hex = mask?.let { IconSig.hex(it) }
        val match = mask?.let { IconSig.match(it) }
        if (mask != null) logIfNew(mask, hex!!, parsed)

        val (glyph, source) = when {
            match != null -> match.first to "icon(d=${match.second})"
            parsed.textGlyph != null -> parsed.textGlyph to "text"
            else -> "•" to "unknown"
        }

        // Once a real instruction is showing, hold it through Maps' in-between notifications:
        // "towards X", no-distance "current road" (e.g. '' / "15th Cross Rd" right after a turn) and "Rerouting...".
        // The destination marker is never held back: it is how arrival is detected.
        val noDistance = heading || parsed.distance.isEmpty()
        val rerouting = text?.startsWith("Rerouting", ignoreCase = true) == true
        if ((noDistance || rerouting) && glyph != "●" && NavState.hasRecentRealInstruction()) {
            NsLog.log("MAPS", "held previous screen over '$title' / '$text'" + if (rerouting) " (rerouting)" else " (no distance)")
            return
        }
        NavState.update(sbn.key, NavState.Info(parsed, glyph, source, hex),
            real = !noDistance && !rerouting) // "Starting navigation…" is not a real instruction yet
    }

    /** Log each new icon shape once (as ASCII art + hex) so it can be labelled in [KnownIcons]. */
    private fun logIfNew(mask: BooleanArray, hex: String, t: NavText) {
        val seen = Prefs.seenIcons
        if (seen.any { IconSig.distance(mask, IconSig.fromHex(it)) <= 8 }) return
        Prefs.seenIcons = seen + hex
        NsLog.log("ICON", "NEW maneuver icon #${seen.size + 1} while '${t.distance}' '${t.road}':\n${IconSig.art(mask)}\nhex=$hex")
    }
}

/** Current Google Maps navigation state, shared with the display service. */
object NavState {
    data class Info(val text: NavText, val glyph: String, val glyphSource: String, val iconHex: String?) {
        /** Same maneuver at the same road = same instruction (distance just counting down). */
        val instructionId get() = "${text.road}|$glyph|$iconHex"
    }

    @Volatile var key: String? = null
        private set
    @Volatile var info: Info? = null
        private set
    @Volatile var listener: ((Info?) -> Unit)? = null

    val active get() = info != null

    private var lastRealAt = 0L

    /** A real (non-heading) instruction arrived in the last 90 s of this trip. */
    fun hasRecentRealInstruction() = info != null && android.os.SystemClock.elapsedRealtime() - lastRealAt < 90_000

    /** [real] is false for the "towards X" heading variant (shown only until a real instruction arrives). */
    fun update(k: String, i: Info, real: Boolean) {
        if (real) lastRealAt = android.os.SystemClock.elapsedRealtime()
        val prev = info
        key = k
        info = i
        if (prev == null) NsLog.log("NAV", "navigation started")
        if (prev?.instructionId != i.instructionId) {
            NsLog.log("NAV", "instruction: glyph=${i.glyph} via ${i.glyphSource} dist='${i.text.distance}' road='${i.text.road}' eta='${i.text.eta}' left='${i.text.remaining}'")
        }
        listener?.invoke(i)
    }

    fun end(reason: String) {
        lastRealAt = 0L
        if (info == null) return
        info = null
        key = null
        NsLog.log("NAV", "navigation ended ($reason)")
        listener?.invoke(null)
    }
}
