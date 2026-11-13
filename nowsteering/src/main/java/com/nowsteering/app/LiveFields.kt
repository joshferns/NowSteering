package com.nowsteering.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Arrived screen's two texts, fixed at the moment of arrival (the road may be updated while it stays up). */
data class Arrived(val road: String, val time: String)

/**
 * Everything one screen needs from the running app, as plain values and lazy suppliers
 * (a supplier is only called when a rule or template asks, so the song-hold and turn-zone state behave as before).
 */
class LiveInputs(
    val settings: Settings,
    val now: Date,
    val speedKmh: Int?,
    /** Milliseconds since the last GPS speed fix; null if there never was one. */
    val speedAgeMs: Long?,
    val arrived: Arrived?,
    /** The instruction on the car, null when nothing may be shown (not navigating, or double-Next went to idle). */
    val nav: NavState.Info?,
    val toTurnMeters: () -> Double?,
    val tripMeters: Double?,
    val song: () -> SessionWatcher.Song?,
    /** Fields from other sources (phone battery, Torque): `car.*`, `phone.battery`. */
    val other: (String) -> FieldValue? = { null },
)

/** The live [RuleContext]: turns [LiveInputs] into fields and situation flags. Pure Kotlin. */
class LiveContext(private val inp: LiveInputs, private val zone: TurnZone, profile: CarProfile = CarProfile.current) : RuleContext {
    override val fields = Fields(profile.neverShow, ::read)

    private val toTurn: Double? by lazy { inp.toTurnMeters() }
    private val near: Boolean by lazy { zone.update(toTurn) }
    private val songNow: SessionWatcher.Song? by lazy { inp.song() }

    override fun flag(name: String): Boolean = when (name) {
        "arrived" -> inp.arrived != null
        "nav.shown" -> inp.nav != null
        "nav.rerouting" -> inp.nav != null && NavTextParser.isRerouting(inp.nav.text.road)
        "near" -> near
        "set.turn_bar" -> inp.settings.turnBar
        "set.keep_on" -> inp.settings.keepOnScreen
        else -> false
    }

    private fun fmt(p: String) = SimpleDateFormat(p, Locale.US).format(inp.now)

    private fun read(name: String): FieldValue? {
        val nav = inp.nav
        val t = nav?.text
        return when (name) {
            "clock" -> FieldValue(fmt("HH:mm"))
            "clock.seconds" -> FieldValue(fmt("HH:mm:ss"))
            "date" -> FieldValue(fmt("EEE d MMM"))
            "speed" -> inp.speedKmh?.let { FieldValue("$it", it.toDouble(), "km/h", inp.speedAgeMs ?: Long.MAX_VALUE) }
            "nav.active" -> if (nav != null) FieldValue("true") else null
            "nav.state" -> when {
                inp.arrived != null -> FieldValue("arrived")
                nav != null && NavTextParser.isRerouting(t!!.road) -> FieldValue("rerouting")
                nav != null -> FieldValue("navigating")
                else -> null
            }
            "turn.arrow" -> nav?.let { FieldValue(it.glyph) }
            "turn.distance" -> nav?.let { n ->
                val m = toTurn
                FieldValue(if (m != null) NavTextParser.formatMeters(m) else n.text.distance)
            }
            "turn.road" -> t?.let { FieldValue(it.road) }
            "turn.progress" -> toTurn?.let { m ->
                val f = 1 - minOf(m, Bars.TURN_BAR_FROM_M) / Bars.TURN_BAR_FROM_M
                FieldValue("${(f * 100).toInt()}", f, "")
            }
            "nav.time_left" -> t?.let { FieldValue(it.timeLeft.ifEmpty { it.remaining.replace(" ", "") }) }
            "nav.distance_left" -> t?.let { FieldValue(it.remaining) }
            "nav.eta" -> t?.let { FieldValue(it.eta) }
            "nav.progress" -> t?.let { x ->
                Bars.tripFraction(NavTextParser.metersOf(x.remaining), inp.tripMeters)?.let { FieldValue("${(it * 100).toInt()}", it) }
            }
            "arrived.road" -> inp.arrived?.let { FieldValue(it.road) }
            "arrived.time" -> inp.arrived?.let { FieldValue(it.time) }
            "song.playing" -> if (songNow != null) FieldValue("true") else null
            "song.title" -> songNow?.takeIf { Glyphs.sanitize(it.title, 40).count { c -> c.isLetterOrDigit() } >= 2 }?.let { FieldValue(it.title) }
            "song.artist" -> songNow?.let { FieldValue(Glyphs.sanitize(it.artist, 40)) }
            "song.text" -> songNow?.let { songBody(it.title, it.artist, inp.settings.songPrefix.extra) }?.let { FieldValue(it) }
            "song.time_left" -> songNow?.let { s ->
                if (songKnown(s)) FieldValue(Layouts.mmss(s.durationMs - s.positionMs!!.coerceAtMost(s.durationMs))) else null
            }
            "song.progress" -> songNow?.let { s ->
                if (songKnown(s)) s.positionMs!!.coerceAtMost(s.durationMs).toDouble().let { p -> FieldValue("${(p / s.durationMs * 100).toInt()}", p / s.durationMs) } else null
            }
            else -> inp.other(name)
        }
    }

    private fun songKnown(s: SessionWatcher.Song) = s.durationMs in 1_000..86_400_000 && s.positionMs != null && s.positionMs >= 0

    companion object {
        /** "Title - Artist" cleaned for the car; null when too little is readable (a title in Devanagari or emoji only). */
        fun songBody(title: String?, artist: String?, extra: String): String? {
            val t = (title ?: "").trim()
            val a = (artist ?: "").trim()
            if (t.isEmpty()) return null
            val body = if (a.isEmpty() || t.contains(a, ignoreCase = true)) t else "$t - $a"
            val clean = Glyphs.sanitize(body, 80, extra)
            return if (clean.count { it.isLetterOrDigit() } < 2) null else clean
        }
    }
}

/** Torque PID id handling: ids come as "05", "0x05", "ff1206" or "0b,Intake…"; compare them in one form. */
object TorqueFields {
    fun normalize(id: String): String {
        val s = id.trim().lowercase().substringBefore(',').substringBefore('|').substringBefore(' ').removePrefix("0x")
        return if (s.length == 1) "0$s" else s
    }

    /** Named car fields and the PID each reads. */
    val named: Map<String, String> = mapOf(
        "car.coolant" to "05",
        "car.voltage" to "42",
        "car.rpm" to "0c",
        "car.trip_kmpl" to "ff1206",   // TODO: confirm this id against a probe log (Trip KPL, not KPL(avg))
        // car.fuel_used: id not yet confirmed; reachable as car.<pid> once known
    )

    /** The PID id behind a field name, or null if it is not a car field or has no PID yet. */
    fun pidOf(field: String): String? = named[field] ?: field.takeIf { it.startsWith("car.") && it.length > 4 }?.removePrefix("car.")?.let { normalize(it) }

    /** Read a car field from a Torque snapshot; missing unless Torque is connected to the car and the PID has a time. */
    fun read(snap: TorqueClient.Snapshot, field: String, nowMs: Long): FieldValue? {
        if (snap.obdConnected != true) return null
        val want = pidOf(field) ?: return null
        val p = snap.pids.firstOrNull { normalize(it.id) == want } ?: return null
        val (v, at) = snap.rows[p.id] ?: return null
        if (at <= 0L || v.isNaN() || v.isInfinite()) return null
        val text = if (Math.abs(v) >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        return FieldValue(text, v, p.unit, (nowMs - at).coerceAtLeast(0))
    }
}
