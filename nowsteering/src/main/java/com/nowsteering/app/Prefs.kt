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

    /** Icon signatures already written to the log, so each new Maps icon is logged once. */
    var seenIcons: Set<String>
        get() = sp.getStringSet("seenIcons", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("seenIcons", v).apply()
}
