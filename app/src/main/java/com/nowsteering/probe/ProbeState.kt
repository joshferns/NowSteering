package com.nowsteering.probe

/** Process-wide probe settings shared by the UI, the media service and the session watcher. */
object ProbeState {
    enum class RefreshMode(val label: String) {
        METADATA_ONLY("a) metadata only, same track"),
        FAKE_TRACK_CHANGE("b) fake track change (new id + duration)"),
        TOGGLE_PLAYBACK("c) pause -> update -> play"),
    }

    @Volatile var mode = RefreshMode.METADATA_ONLY
    @Volatile var requestAudioFocus = true
    @Volatile var mirrorTarget = false
    @Volatile var forwardToTarget = false

    // Text currently held by our session.
    @Volatile var title = "Now Steering"
    @Volatile var artist = "Phase 0 probe"
    @Volatile var album = "NS"

    /** Last button / transport event, shown in large text. */
    @Volatile var flash = "(no button yet)"

    const val YTM_PACKAGE = "com.google.android.apps.youtube.music"
}
