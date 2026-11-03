package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideSessionPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RideSessionMetricsCalculatorTest {
    private val startTime = 1_700_000_000_000L

    @Test
    fun `average speed is reliable route distance divided by ride duration`() {
        val points = listOf(
            point(12.9716, startTime, speed = 2f),
            point(12.9726, startTime + 10_000, speed = 2f)
        )
        val result = RideSessionMetricsCalculator.calculate(points, durationMillis = 20_000)
        assertNotNull(result.distanceMeters)
        assertEquals(result.distanceMeters!! / 20.0, result.averageSpeedMetersPerSecond!!, 0.0001)
    }

    @Test
    fun `isolated raw speed spike is not reported as maximum`() {
        val points = listOf(
            point(12.9716, startTime, speed = 5f),
            point(12.9717, startTime + 5_000, speed = 52.7f),
            point(12.9718, startTime + 10_000, speed = 5f)
        )
        val result = RideSessionMetricsCalculator.calculate(points, durationMillis = 60_000)
        assertNotNull(result.distanceMeters)
        assertNull(result.maximumSpeedMetersPerSecond)
    }

    @Test
    fun `consistent fast consecutive segments retain actual high speed`() {
        val points = listOf(
            point(12.9716, startTime, speed = 60f),
            point(12.97214, startTime + 1_000, speed = 60f),
            point(12.97268, startTime + 2_000, speed = 60f)
        )
        val result = RideSessionMetricsCalculator.calculate(points, durationMillis = 2_000)
        assertNotNull(result.maximumSpeedMetersPerSecond)
        assertEquals(60.0, result.maximumSpeedMetersPerSecond!!, 3.0)
    }

    private fun point(latitude: Double, timestamp: Long, speed: Float) =
        RideSessionPoint(latitude, 77.5946, timestamp, 5f, speed)
}