package com.nowsteering.app

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardGuardTest {
    private fun ok(state: Int?, quiet: Long?) = ForwardGuard.check(state, quiet).ok

    @Test fun neverForwardsWhileLoadingOrBetweenTracks() {
        // 11 Nov 08:47:24 and 08:49:30: forwarded while the player was NONE, ERROR followed 2 s later.
        assertFalse(ok(PlaybackState.STATE_NONE, 400))
        assertFalse(ok(PlaybackState.STATE_NONE, null))
        assertFalse(ok(PlaybackState.STATE_STOPPED, 10_000))
        assertFalse(ok(PlaybackState.STATE_BUFFERING, 10_000))
        assertFalse(ok(PlaybackState.STATE_CONNECTING, 10_000))
        assertFalse(ok(PlaybackState.STATE_SKIPPING_TO_NEXT, 10_000))
        assertFalse(ok(null, 10_000))
    }

    @Test fun leavesAnErroredAppAlone() {
        // 08:47:38: a skip sent to an app in ERROR did nothing.
        val d = ForwardGuard.check(PlaybackState.STATE_ERROR, 60_000)
        assertFalse(d.ok)
        assertTrue(d.reason, d.reason.contains("ERROR"))
    }

    @Test fun waitsTwoSecondsAfterATrackChangeOrSettling() {
        assertFalse(ok(PlaybackState.STATE_PLAYING, 0))
        assertFalse(ok(PlaybackState.STATE_PLAYING, 1999))
        assertTrue(ok(PlaybackState.STATE_PLAYING, 2000))
        assertFalse(ok(PlaybackState.STATE_PAUSED, 500))
        assertTrue(ok(PlaybackState.STATE_PAUSED, 5_000))
    }

    @Test fun forwardsNormallyWhenSettled() {
        assertTrue(ok(PlaybackState.STATE_PLAYING, null))   // nothing seen yet since we started watching
        assertTrue(ok(PlaybackState.STATE_PLAYING, 3_600_000))
        assertEquals("", ForwardGuard.check(PlaybackState.STATE_PLAYING, 5_000).reason)
    }

    @Test fun droppedReasonsNameTheCause() {
        assertTrue(ForwardGuard.check(PlaybackState.STATE_NONE, 100).reason.contains("NONE"))
        assertTrue(ForwardGuard.check(PlaybackState.STATE_PLAYING, 700).reason.contains("700 ms"))
    }
}
