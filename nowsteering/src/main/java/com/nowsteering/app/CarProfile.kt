package com.nowsteering.app

/**
 * Everything the app knows about one car's head unit, as data. Phase 1 of v2 has exactly one: the 2014 Honda City
 * ("Honda HFT") measured in Phase 0 and 1. Faces ask the profile for widths and glyphs instead of hard-coding them.
 */
data class CarProfile(
    val id: String,
    val name: String,
    /** Widest text the head unit was measured to show: title (big line), album (line 2), artist (line 3). */
    val measuredTitle: Int,
    val measuredLine2: Int,
    val measuredLine3: Int,
    /** The widths the Current preset has always used (a little under the measured ones, to be safe). */
    val safeTitle: Int,
    val safeLine: Int,
    /** Song and 20-wide lines. */
    val songLine: Int,
    /** Glyphs beyond ASCII and Latin-1 that were seen to draw. */
    val confirmedGlyphs: String,
    /** Glyphs that were seen to draw as dots (or not at all), so faces must not rely on them. */
    val failingGlyphs: String,
    /** The head unit keeps up with about one change per second. */
    val refreshMinMs: Long,
    /** Steering-wheel behaviour. */
    val nextTapWindowMs: Int,
    val forwardSettleMs: Long,
    /** Row order on the head unit, top to bottom. */
    val rowOrder: List<String>,
    /** Fields a face must never show for this car (they exist, but their numbers are wrong). */
    val neverShow: Set<String>,
    val quirks: List<String>,
) {
    companion object {
        /** `const` mirrors, for places that need compile-time constants (Glyphs, DisplayEngine, ForwardGuard). */
        const val HONDA_SAFE_TITLE = 15
        const val HONDA_SAFE_LINE = 18
        const val HONDA_SONG_LINE = 20
        const val HONDA_CONFIRMED = "←→↑↓↖↗•●▲▶▼█▓▒▄"
        const val HONDA_REFRESH_MS = 1000L
        const val HONDA_SETTLE_MS = 2000L

        /** Phase 1 has no picker: this is the car. */
        val HONDA_CITY_2014 = CarProfile(
            id = "honda_city_2014",
            name = "Honda City 2014 (Honda HFT)",
            measuredTitle = 17, measuredLine2 = 20, measuredLine3 = 20,
            safeTitle = HONDA_SAFE_TITLE, safeLine = HONDA_SAFE_LINE, songLine = HONDA_SONG_LINE,
            confirmedGlyphs = HONDA_CONFIRMED,
            failingGlyphs = "░▀⇐♪♫♩♬♭♯♠♣♥♦☼◊ and Devanagari / emoji",
            refreshMinMs = HONDA_REFRESH_MS,
            nextTapWindowMs = 600,
            forwardSettleMs = HONDA_SETTLE_MS,
            rowOrder = listOf("title", "album", "artist"),
            neverShow = setOf("car.fuel_level", "car.fuel_remaining"),
            quirks = listOf(
                "no scrolling: text longer than the line is cut",
                "Next / Previous are never forwarded within 2 s of a track change or while the app is loading",
                "pressing the head-unit mic starts the call screen",
                "the car clock runs about 1 minute fast",
                "fuel level from the ECU is unreliable (2.75 % with 72 km to empty): never shown",
            ),
        )

        val current: CarProfile get() = HONDA_CITY_2014
    }
}
