package com.nowsteering.app

/** What the cost maths needs. Distances in km, money in rupees, fuel in litres. Null = not known (or stale). */
data class TripInputs(
    val price: Double?,
    val typicalKmpl: Double?,
    /** Distance driven since the trip started (GPS), or null when no trip is running. */
    val distanceKm: Double?,
    val fuelUsedL: Double?,
    val tripKmpl: Double?,
    val longTermKmpl: Double?,
    val distanceLeftKm: Double?,
)

data class TripCosts(
    val kmpl: Double?,
    /** Where [kmpl] came from: "trip", "long-term" or "typical". */
    val kmplSource: String,
    val cost: Double?,
    val costSource: String,
    val costToGo: Double?,
    val total: Double?,
    val per10Km: Double?,
)

object TripMath {
    const val MIN_KM_FOR_TRIP_AVERAGE = 2.0

    private fun plausible(k: Double?) = k?.takeIf { it in 1.0..100.0 }

    /**
     * km/L used for the distance still to go: Torque's trip average once 2 km are driven this trip; before that (or if it is
     * missing) Torque's long-term average; without Torque, the user's typical km/L.
     * Cost so far = fuel used x price; with no fuel-used reading, distance / typical km/L x price. Every output is null
     * when what it needs is not known (a price that is not set makes everything null).
     */
    fun compute(i: TripInputs): TripCosts {
        val price = i.price?.takeIf { it > 0 }
        val trip = plausible(i.tripKmpl)
        val long = plausible(i.longTermKmpl)
        val typical = plausible(i.typicalKmpl)
        val (kmpl, src) = when {
            trip != null && (i.distanceKm ?: 0.0) >= MIN_KM_FOR_TRIP_AVERAGE -> trip to "trip"
            long != null -> long to "long-term"
            typical != null -> typical to "typical"
            else -> null to "none"
        }
        val fuel = i.fuelUsedL?.takeIf { it >= 0 }
        val cost: Double?
        val costSrc: String
        when {
            price == null -> { cost = null; costSrc = "no price" }
            fuel != null -> { cost = fuel * price; costSrc = "fuel used x price" }
            typical != null && i.distanceKm != null -> { cost = i.distanceKm / typical * price; costSrc = "distance / typical km/L x price" }
            else -> { cost = null; costSrc = "no fuel reading" }
        }
        val toGo = if (price != null && kmpl != null && i.distanceLeftKm != null) i.distanceLeftKm / kmpl * price else null
        val total = if (cost != null && toGo != null) cost + toGo else null
        val per10 = if (price != null && kmpl != null) 10.0 / kmpl * price else null
        return TripCosts(kmpl, src, cost, costSrc, toGo, total, per10)
    }

    /** Whole rupees, as the car shows them. */
    fun rupees(v: Double): String = Math.round(v).toString()
}

/**
 * One trip: from the car's Bluetooth connect to its disconnect. Distance is integrated from GPS speed; fuel comes from
 * Torque's own trip counter (see docs/FIELDS.md). Kept in memory only: the process restarting starts a new trip.
 */
class TripTracker(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val log: (String) -> Unit = { NsLog.log("TRIP", it) },
) {
    var active = false
        private set
    var startedAt = 0L
        private set
    private var meters = 0.0
    val distanceKm: Double get() = meters / 1000

    // The last good readings, so the disconnect line still has numbers when Torque goes quiet at the same moment.
    private var lastFuel: Double? = null
    private var lastCosts: TripCosts? = null
    private var lastKmplTrip: Double? = null
    private var lastLongKmpl: Double? = null

    fun start(why: String) {
        if (active) return
        active = true; startedAt = now(); meters = 0.0
        lastFuel = null; lastCosts = null; lastKmplTrip = null; lastLongKmpl = null
        log("trip started ($why)")
    }

    fun end(why: String) {
        if (!active) return
        log("trip ended ($why): ${summary()}")
        active = false
    }

    fun addMeters(m: Double) { if (active && m > 0) meters += m }

    fun observe(i: TripInputs, c: TripCosts) {
        i.fuelUsedL?.let { lastFuel = it }
        i.tripKmpl?.let { lastKmplTrip = it }
        i.longTermKmpl?.let { lastLongKmpl = it }
        if (c.cost != null || c.kmpl != null) lastCosts = c
    }

    fun summary(): String {
        val c = lastCosts
        val f = { v: Double? -> v?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "?" }
        val minutes = ((now() - startedAt) / 60_000)
        return "distance ${String.format(java.util.Locale.US, "%.2f", distanceKm)} km in $minutes min, fuel used ${f(lastFuel)} L, " +
            "trip km/L ${f(lastKmplTrip)}, long-term km/L ${f(lastLongKmpl)}, km/L used ${f(c?.kmpl)} (${c?.kmplSource ?: "none"}), " +
            "cost Rs ${c?.cost?.let { TripMath.rupees(it) } ?: "?"} (${c?.costSource ?: "?"})"
    }

    fun logPeriodic() { if (active) log(summary()) }
}
