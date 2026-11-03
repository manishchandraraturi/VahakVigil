package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveLocationAvailabilityGateTest {
    @Test fun `initial unavailable report keeps acquisition state`() {
        val gate = LiveLocationAvailabilityGate()
        assertNull(gate.onAvailabilityChanged(false))
    }

    @Test fun `unavailable after a live fix reports temporary loss`() {
        val gate = LiveLocationAvailabilityGate()
        gate.onValidFix()
        assertEquals(RideLocationState.LocationTemporarilyUnavailable, gate.onAvailabilityChanged(false))
        assertNull(gate.onAvailabilityChanged(true))
    }

    @Test fun `watchdog leaves initial acquisition alone until timeout`() {
        val gate = LiveLocationAvailabilityGate()
        assertNull(gate.onWatchdog(29_999L, 0L, 30_000L, 20_000L))
        assertEquals(RideLocationState.LocationUnavailable, gate.onWatchdog(30_000L, 0L, 30_000L, 20_000L))
    }

    @Test fun `watchdog marks silent live stream lost and accepts recovery fix`() {
        val gate = LiveLocationAvailabilityGate()
        gate.onValidFix(10_000L)
        assertNull(gate.onWatchdog(29_999L, 0L, 30_000L, 20_000L))
        assertEquals(RideLocationState.LocationTemporarilyUnavailable, gate.onWatchdog(30_000L, 0L, 30_000L, 20_000L))
        gate.onValidFix(31_000L)
        assertNull(gate.onWatchdog(31_001L, 0L, 30_000L, 20_000L))
    }
}
