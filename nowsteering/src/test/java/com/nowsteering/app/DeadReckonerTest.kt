package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadReckonerTest {
    private var t = 0L
    private val dr = DeadReckoner { t }

    private fun maps(m: Int, id: String = "A") = dr.onMaps(id, "$m m", m.toDouble())
    private fun drive(seconds: Int, mps: Double) = repeat(seconds) { t += 1000; dr.onSpeed(mps, 1000) }

    @Test fun showsMapsWhileMapsIsUpdating() {
        maps(300)
        drive(1, 10.0); maps(290)
        drive(1, 10.0); maps(280)
        assertEquals(280.0, dr.shown()!!, 0.1) // no counting ahead of a fresh Maps value
    }

    @Test fun countsDownInGapsAndCorrectsDown() {
        maps(450)
        drive(6, 10.0) // Maps quiet (phone locked): seconds 2-6 counted
        assertEquals(400.0, dr.shown()!!, 0.1)
        maps(395)
        assertEquals(395.0, dr.shown()!!, 0.1)
    }

    @Test fun replayOf2NovMorningNeverSitsBelowMaps() {
        // Maps updated every ~1-2 s, each time ~20 m above where a GPS-only count would be.
        var mapsM = 300
        maps(mapsM)
        var worstBelowMaps = 0.0
        for (step in 0 until 28) {
            drive(if (step % 3 == 0) 2 else 1, 10.0)
            mapsM -= 10
            maps(mapsM)
            worstBelowMaps = maxOf(worstBelowMaps, mapsM - dr.shown()!!)
        }
        assertTrue("showed ${worstBelowMaps} m below Maps", worstBelowMaps <= 10)
        assertEquals(mapsM.toDouble(), dr.shown()!!, 30.0)
    }

    @Test fun singleBlipIgnoredRepeatedHigherPauses() {
        maps(300)
        drive(5, 10.0) // gap: seconds 2-5 counted, shows 260
        assertEquals(260.0, dr.shown()!!, 0.1)
        maps(280) // blip, ignored
        assertEquals(260.0, dr.shown()!!, 0.1)
        drive(1, 10.0)
        maps(275) // still higher: pause
        drive(2, 10.0)
        assertEquals(260.0, dr.shown()!!, 0.1)
        maps(255) // Maps caught up: follow Maps again
        assertEquals(255.0, dr.shown()!!, 0.1)
    }

    @Test fun holdsTheLastValueWhenMapsStaysSilent() {
        // Seen on 7 Nov: after 90 s without a Maps update the display jumped back up to Maps' stale value.
        maps(5000)
        drive(100, 10.0)              // 90 s are counted, the last 10 s are not
        val held = dr.shown()!!
        assertTrue("held $held", held < 4500)  // still counting down, not reverted to 5000
        drive(30, 10.0)
        assertEquals(held, dr.shown()!!, 0.1)  // frozen, neither reverting nor guessing further
    }

    @Test fun bigCorrectionJumpsToMaps() {
        maps(300)
        drive(10, 10.0)
        maps(290) // 70 m above our 220
        assertEquals(290.0, dr.shown()!!, 0.1)
    }

    @Test fun staleRepeatIsIgnoredAndNewInstructionResets() {
        maps(500)
        drive(6, 10.0)
        maps(500) // same text again: Maps is stale, keep counting
        assertEquals(450.0, dr.shown()!!, 0.1)
        maps(800, id = "B")
        assertEquals(800.0, dr.shown()!!, 0.1)
    }
}
