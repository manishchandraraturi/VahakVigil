package com.manish.vahakvigil.domain.emergency

import com.manish.vahakvigil.domain.model.EmergencyCountdownRecovery
import com.manish.vahakvigil.domain.model.EmergencyLifecyclePhase
import com.manish.vahakvigil.domain.model.EmergencyLifecycleRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyCountdownRecoveryTest {
    @Test fun sameBootRecoveryUsesMonotonicDeadline() {
        val record = record(bootCount = 5, deadlineElapsed = 75_000, deadlineEpoch = 1_700_000_075_000)
        assertEquals(30_000L, EmergencyCountdownRecovery.remainingMillis(record, 1_700_000_100_000, 45_000, 5))
    }

    @Test fun rebootRecoveryUsesPersistedWallDeadlineAndNeverRestartsFullDuration() {
        val record = record(bootCount = 5, deadlineElapsed = 75_000, deadlineEpoch = 1_700_000_075_000)
        assertEquals(20_000L, EmergencyCountdownRecovery.remainingMillis(record, 1_700_000_055_000, 2_000, 6))
        assertEquals(0L, EmergencyCountdownRecovery.remainingMillis(record, 1_700_000_080_000, 2_000, 6))
    }

    @Test fun wallClockRollbackAcrossBootExpiresConservatively() {
        val record = record(bootCount = 5, deadlineElapsed = 75_000, deadlineEpoch = 1_700_000_075_000)
        assertEquals(0L, EmergencyCountdownRecovery.remainingMillis(record, 1_699_999_000_000, 1_000, 6))
    }

    private fun record(bootCount: Int, deadlineElapsed: Long, deadlineEpoch: Long) = EmergencyLifecycleRecord(
        incidentId = "incident-uuid",
        incidentCreatedAtEpochMillis = 1_700_000_000_000,
        windowStartEpochMillis = 1_699_999_990_000,
        windowEndEpochMillis = 1_700_000_000_000,
        confidenceScore = .8f,
        phase = EmergencyLifecyclePhase.COUNTING_DOWN,
        deadlineElapsedRealtimeMillis = deadlineElapsed,
        deadlineEpochMillis = deadlineEpoch,
        bootCount = bootCount
    )
}
