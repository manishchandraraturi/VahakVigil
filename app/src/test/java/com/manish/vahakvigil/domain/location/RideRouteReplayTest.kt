package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideSessionPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RideRouteReplayTest {
    @Test
    fun `replay retains actual reliable points and rejects impossible jump`() {
        val start = RideSessionPoint(12.9716, 77.5946, 1_700_000_000_000L, 5f, 8f)
        val impossibleJump = RideSessionPoint(12.9916, 77.5946, 1_700_000_005_000L, 5f, 8f)
        val end = RideSessionPoint(12.9720, 77.5946, 1_700_000_010_000L, 5f, 8f)

        assertEquals(listOf(start, end), RideRouteReplay.filterReliablePoints(listOf(start, impossibleJump, end)))
    }
}
