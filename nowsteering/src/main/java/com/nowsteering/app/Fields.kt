package com.nowsteering.app

/** Where a field's value comes from. Any source can be missing; the app keeps working without it. */
enum class Source { PHONE, MAPS, MUSIC, TORQUE }

/**
 * A named value as a source reports it. [text] is how it is written on a face; [number] is the same value as a number
 * (for bars and later rules); [ageMs] is how long ago the source last updated it (0 = just now).
 */
data class FieldValue(val text: String, val number: Double? = null, val unit: String = "", val ageMs: Long = 0)

/** One entry of the field catalogue ([docs/FIELDS.md] lists the same fields; a test keeps them in step). */
data class FieldDef(val name: String, val source: Source, val unit: String, val maxAgeMs: Long?, val doc: String)

object FieldCatalog {
    const val TORQUE_MAX_AGE_MS = 10_000L
    const val SPEED_MAX_AGE_MS = 5_000L
    const val PHONE_BATTERY_MAX_AGE_MS = 120_000L

    private fun d(name: String, src: Source, unit: String, maxAge: Long?, doc: String) = FieldDef(name, src, unit, maxAge, doc)

    val all: List<FieldDef> = listOf(
        d("clock", Source.PHONE, "HH:mm", null, "Local time"),
        d("clock.seconds", Source.PHONE, "HH:mm:ss", null, "Local time with seconds"),
        d("date", Source.PHONE, "EEE d MMM", null, "Local date, e.g. Sat 7 Nov"),
        d("speed", Source.PHONE, "km/h", SPEED_MAX_AGE_MS, "GPS speed, whole km/h (0 below 2)"),
        d("phone.battery", Source.PHONE, "%", PHONE_BATTERY_MAX_AGE_MS, "Phone battery level"),
        d("nav.active", Source.MAPS, "", null, "\"true\" while Maps is navigating and the instruction is on the car"),
        d("nav.state", Source.MAPS, "", null, "navigating, rerouting or arrived"),
        d("turn.arrow", Source.MAPS, "", null, "Maneuver glyph for the next turn"),
        d("turn.distance", Source.MAPS, "m / km", null, "Distance to the next turn, counted down by GPS when Maps is quiet"),
        d("turn.road", Source.MAPS, "", null, "Road of the next turn"),
        d("turn.progress", Source.MAPS, "0..1", null, "Progress over the last 300 m before the turn"),
        d("nav.time_left", Source.MAPS, "", null, "Maps' trip time left (distance left when Maps gives no time)"),
        d("nav.distance_left", Source.MAPS, "m / km", null, "Maps' distance left on the trip"),
        d("nav.eta", Source.MAPS, "", null, "Maps' arrival time"),
        d("nav.progress", Source.MAPS, "0..1", null, "Trip progress: 1 - distance left / longest distance left this trip"),
        d("arrived.road", Source.MAPS, "", null, "Road shown on the Arrived screen"),
        d("arrived.time", Source.PHONE, "HH:mm", null, "Clock time at arrival"),
        d("song.title", Source.MUSIC, "", null, "Playing title (missing when too little of it is readable)"),
        d("song.artist", Source.MUSIC, "", null, "Playing artist, cleaned for the car"),
        d("song.text", Source.MUSIC, "", null, "\"Title - Artist\" as the nav screen shows it (artist dropped if empty or in the title)"),
        d("song.time_left", Source.MUSIC, "m:ss", null, "Time left in the song (missing for live streams)"),
        d("song.progress", Source.MUSIC, "0..1", null, "How far through the song (missing for live streams)"),
        d("song.playing", Source.MUSIC, "", null, "\"true\" while a music app is playing"),
        d("car.coolant", Source.TORQUE, "°C", TORQUE_MAX_AGE_MS, "Engine coolant temperature (PID 05)"),
        d("car.voltage", Source.TORQUE, "V", TORQUE_MAX_AGE_MS, "Control module voltage (PID 42, not the adapter's Volts(Ad))"),
        d("car.rpm", Source.TORQUE, "rpm", TORQUE_MAX_AGE_MS, "Engine speed (PID 0c)"),
        d("car.trip_kmpl", Source.TORQUE, "km/l", TORQUE_MAX_AGE_MS, "Trip average fuel economy (Torque 'Trip KPL', not the long-term KPL(avg))"),
        d("car.fuel_used", Source.TORQUE, "l", TORQUE_MAX_AGE_MS, "Fuel used this trip"),
    )

    private val byName = all.associateBy { it.name }

    /** `car.<pid>` is any Torque PID by its id (e.g. `car.0b`); it ages out like the named car fields. */
    fun def(name: String): FieldDef? = byName[name]
        ?: if (name.startsWith("car.") && name.length > 4) FieldDef(name, Source.TORQUE, "", TORQUE_MAX_AGE_MS, "Torque PID ${name.removePrefix("car.")}") else null

    fun maxAgeMs(name: String): Long? = def(name)?.maxAgeMs
}

/**
 * One lookup session for one screen. A field is missing when its source gave nothing, when it is blank, when it is
 * on the profile's never-show list, or when it has reached its age limit (so a stale number is never shown).
 * Each field is read from its source at most once per session, and only when something asks for it.
 */
class Fields(
    private val neverShow: Set<String> = emptySet(),
    private val read: (String) -> FieldValue? = { null },
) {
    private val cache = HashMap<String, FieldValue?>()

    fun get(name: String): FieldValue? = cache.getOrPut(name) {
        if (name in neverShow) return@getOrPut null
        val v = read(name) ?: return@getOrPut null
        if (v.text.isBlank()) return@getOrPut null
        val limit = FieldCatalog.maxAgeMs(name)
        if (limit != null && v.ageMs >= limit) null else v
    }

    fun text(name: String): String? = get(name)?.text
    fun number(name: String): Double? = get(name)?.number
}
