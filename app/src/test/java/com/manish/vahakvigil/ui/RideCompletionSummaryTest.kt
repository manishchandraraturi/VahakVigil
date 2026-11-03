package com.manish.vahakvigil.ui

import com.manish.vahakvigil.domain.model.RideCompletionSummary
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCompletionSummaryTest {
    @Test fun incompleteAndInterruptedRidesAreClearlyMarked() {
        assertTrue(RideCompletionSummary.from(session(RideSessionStatus.INCOMPLETE), 1).hasIncompleteGps)
        assertTrue(RideCompletionSummary.from(session(RideSessionStatus.INTERRUPTED), 8).hasIncompleteGps)
        assertTrue(RideCompletionSummary.from(session(RideSessionStatus.COMPLETED), 0).hasIncompleteGps)
    }

    @Test fun summaryPreservesUnknownDistanceAndPendingFinalization() {
        val summary = RideCompletionSummary.from(session(RideSessionStatus.ACTIVE, endedAt = null), 0)
        assertFalse(summary.isFinalized)
        assertNull(summary.session.distanceMeters)
    }

    private fun session(status: RideSessionStatus, endedAt: Long? = 2_000L) = RideSession(
        id = "ride-1", startedAtEpochMillis = 1_000L, endedAtEpochMillis = endedAt,
        startLatitude = null, startLongitude = null, endLatitude = null, endLongitude = null,
        durationMillis = 1_000L, distanceMeters = null, averageSpeedMetersPerSecond = null,
        maximumSpeedMetersPerSecond = null, status = status
    )
}
