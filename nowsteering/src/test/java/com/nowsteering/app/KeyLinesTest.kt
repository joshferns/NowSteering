package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLinesTest {
    private val log = """
        11-11 08:47:23.320 [KEY] KEYCODE_MEDIA_NEXT (87) UP repeat=0
        11-11 08:47:23.400 [MAPS] raw key=0|com.google.android.apps.maps title='300 m'
        11-11 08:47:23.505 [KEY] KEYCODE_MEDIA_NEXT (87) DOWN repeat=0
        11-11 08:47:24.119 [FWD] next DROPPED, not sent to youtube: app is NONE
        11-11 08:47:24.200 [GPS] fixes coming in
        11-11 08:47:25.000 [SESSIONS] YTMusic metadata: title='Falling' artist='Trevor Daniel'
        11-11 08:47:25.100 [SESSIONS] YTMusic metadata: title='Falling' artist='Trevor Daniel'
        11-11 08:47:26.000 [SCREEN] sent '17:03 32 km/h' / '▶ Falling' / '2:31 left ███▒▒▒'
        11-11 08:47:27.000 [SCREEN] sent '17:03 32 km/h' / 'Sun 1 Nov' / 'Now Steering'
        11-11 08:47:28.000 [NOTE] Final check start
    """.trimIndent()

    @Test fun keepsTheLinesThatMatter() {
        val kept = KeyLines.filter(log)
        assertTrue(kept.any { it.contains("[KEY]") && it.contains("DOWN") })
        assertTrue(kept.any { it.contains("[FWD]") && it.contains("DROPPED") })
        assertTrue(kept.any { it.contains("[NOTE]") })
        assertTrue(kept.any { it.contains("▶ Falling") })       // mirror screen
    }

    @Test fun dropsNoise() {
        val kept = KeyLines.filter(log)
        assertFalse(kept.any { it.contains("[MAPS]") })
        assertFalse(kept.any { it.contains("[GPS]") })
        assertFalse(kept.any { it.contains("Now Steering'") })  // plain clock screens
    }

    @Test fun collapsesRepeatedLines() {
        val kept = KeyLines.filter(log)
        assertEquals(1, kept.count { it.contains("metadata: title='Falling'") })
    }

    @Test fun staysSmallOnABigLog() {
        // 750 KB of mostly per-second noise (11-12 Nov) must come out far below the old 200 000 character cut.
        val noisy = buildString {
            repeat(9000) { i -> appendLine("11-12 10:%02d:%02d.000 [SCREEN] sent '10:00 %d km/h' / 'x' / 'y'".format(i / 60 % 60, i % 60, i % 40)) }
            repeat(9000) { i -> appendLine("11-12 10:00:00.000 [MAPS] raw key=$i title='300 m' text='13th Main Rd' sub='7 min · 2.6 km · 07:04 ETA'") }
            appendLine("11-12 10:30:00.000 [KEY] KEYCODE_MEDIA_NEXT (87) DOWN repeat=0")
        }
        assertTrue(noisy.length > 750_000)
        val kept = KeyLines.filter(noisy)
        assertEquals(1, kept.size)
    }
}
