package com.nowsteering.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TorquePidTest {
    @Test fun parsesPlainInfo() {
        val p = TorquePid.parseInfo("0x05", "Engine Coolant Temperature,Coolant,°C,215,-40,1", true, true)
        assertEquals("Engine Coolant Temperature", p.name); assertEquals("Coolant", p.shortName); assertEquals("°C", p.unit)
    }

    @Test fun nameWithCommaKeepsUnitAndShortName() {
        val p = TorquePid.parseInfo("ff", "Fuel, trip average,Trip avg,km/l,100,0,1", false, true)
        assertEquals("Fuel, trip average", p.name); assertEquals("Trip avg", p.shortName); assertEquals("km/l", p.unit)
    }

    @Test fun missingInfoFallsBackToId() {
        assertEquals("0x0c", TorquePid.parseInfo("0x0c", null, false, false).name)
    }

    @Test fun staleAndMissingValuesShowDash() {
        assertEquals("—", TorquePid.valueText(null, null, 1000))
        assertEquals("—", TorquePid.valueText(5.0, 0, 1000))
        assertEquals("— (stale)", TorquePid.valueText(5.0, 1000, 20_000))
        assertEquals("14.27", TorquePid.valueText(14.27, 19_500, 20_000))
        assertEquals("88", TorquePid.valueText(88.0, 19_500, 20_000))
        assertEquals("2150", TorquePid.valueText(2150.0, 19_500, 20_000))
    }
}
