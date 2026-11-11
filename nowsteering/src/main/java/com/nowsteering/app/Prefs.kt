package com.nowsteering.app

import android.content.Context
import android.content.SharedPreferences

/** The few persisted settings: which car, auto-start on/off, tap window. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("ns", Context.MODE_PRIVATE)
    }

    var carAddress: String?
        get() = sp.getString("carAddress", null)
        set(v) = sp.edit().putString("carAddress", v).apply()

    var carName: String?
        get() = sp.getString("carName", null)
        set(v) = sp.edit().putString("carName", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", true)
        set(v) = sp.edit().putBoolean("autoStart", v).apply()

    var tapWindowMs: Int
        get() = sp.getInt("tapWindowMs", 600)
        set(v) = sp.edit().putInt("tapWindowMs", v).apply()

    /** How a text change is pushed to the car (see SteeringService.RefreshStyle). */
    var refreshStyle: String
        get() = sp.getString("refreshStyle", "METADATA_ONLY") ?: "METADATA_ONLY"
        set(v) = sp.edit().putString("refreshStyle", v).apply()

    /** Seconds on the idle clock, so screen refresh can be checked while parked. */
    var idleSeconds: Boolean
        get() = sp.getBoolean("idleSeconds", false)
        set(v) = sp.edit().putBoolean("idleSeconds", v).apply()

    /** Turn bar in the last 300 m before a turn (replaces the ETA line). */
    var turnBar: Boolean
        get() = sp.getBoolean("turnBar", true)
        set(v) = sp.edit().putBoolean("turnBar", v).apply()

    /** Short trip-progress bar after the ETA. */
    var tripBar: Boolean
        get() = sp.getBoolean("tripBar", true)
        set(v) = sp.edit().putBoolean("tripBar", v).apply()

    /** Re-claim the car (screen + wheel buttons) whenever another app starts playing. Off by default. */
    var keepOnScreen: Boolean
        get() = sp.getBoolean("keepOnScreen", false)
        set(v) = sp.edit().putBoolean("keepOnScreen", v).apply()

    /** Arrival clock on line 2 while far from a turn (replaces the trip bar there). Off until the width test passes. */
    var arrivalTime: Boolean
        get() = sp.getBoolean("arrivalTime", false)
        set(v) = sp.edit().putBoolean("arrivalTime", v).apply()

    /**
     * Glyph before the song on the nav screen; ▶ by default. In 0.3.0 the only choices were none / • / ♪, and • was
     * picked because ♪ shows as a dot, so a stored • or ♪ is switched to ▶ once. A deliberate "none" is kept.
     */
    var songPrefix: SongPrefix
        get() {
            if (!sp.getBoolean("songPrefixArrowDefault", false)) {
                val old = sp.getString("songPrefix", null)
                val migrated = if (old == null || old == "DOT" || old == "NOTE") SongPrefix.ARROW else SongPrefix.of(old)
                sp.edit().putString("songPrefix", migrated.name).putBoolean("songPrefixArrowDefault", true).apply()
            }
            return SongPrefix.of(sp.getString("songPrefix", null))
        }
        set(v) = sp.edit().putString("songPrefix", v.name).apply()

    /** Icon signatures already written to the log, so each new Maps icon is logged once. */
    var seenIcons: Set<String>
        get() = sp.getStringSet("seenIcons", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("seenIcons", v).apply()
}
