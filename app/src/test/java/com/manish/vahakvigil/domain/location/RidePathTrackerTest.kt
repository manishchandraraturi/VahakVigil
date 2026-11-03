package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideLocationPoint
import com.manish.vahakvigil.domain.model.RideLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Focused unit tests for [RidePathTracker] verifying:
 * 1. Valid point accepted.
 * 2. Invalid coordinates rejected.
 * 3. Inaccurate points rejected.
 * 4. Stale points rejected.
 * 5. Duplicate and near-duplicate points filtered (stationary jitter).
 * 6. Bounded path size (memory safety).
 * 7. Path reset on new ride.
 * 8. Path cleanup on ride stop.
 */
class RidePathTrackerTest {

    private lateinit var tracker: RidePathTracker
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        tracker = RidePathTracker(
            maxPoints = 10,
            maxAcceptableAccuracyMeters = RidePathTracker.MAX_ACCEPTABLE_ACCURACY_METERS,
            minDisplacementMeters = 3.0,
            maxStalenessMillis = 60_000L
        )
    }

    // ─── 1. Valid Point Accepted ─────────────────────────────────────────────

    @Test
    fun `valid point is accepted and recorded in path`() {
        val point = RideLocationPoint(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 10.0f,
            timestampMs = now
        )

        val accepted = tracker.addPoint(point, currentTimeMs = now)

        assertTrue("Valid point must be accepted", accepted)
        assertEquals(1, tracker.size())
        assertEquals(point, tracker.getCurrentPath().first())
    }

    // ─── 2. Invalid Coordinates Rejected ─────────────────────────────────────

    @Test
    fun `invalid latitude or longitude is rejected`() {
        val invalidLat = RideLocationPoint(latitude = 95.0, longitude = 77.0, accuracyMeters = 5f, timestampMs = now)
        val invalidLon = RideLocationPoint(latitude = 12.0, longitude = 185.0, accuracyMeters = 5f, timestampMs = now)
        val nanCoord = RideLocationPoint(latitude = Double.NaN, longitude = 77.0, accuracyMeters = 5f, timestampMs = now)

        assertFalse(tracker.addPoint(invalidLat, currentTimeMs = now))
        assertFalse(tracker.addPoint(invalidLon, currentTimeMs = now))
        assertFalse(tracker.addPoint(nanCoord, currentTimeMs = now))
        assertEquals(0, tracker.size())
    }

    // ─── 3. Inaccurate Points Rejected ───────────────────────────────────────

    @Test
    fun `points with accuracy exceeding threshold are rejected`() {
        val inaccuratePoint = RideLocationPoint(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 75.0f, // Exceeds 50m max
            timestampMs = now
        )

        val accepted = tracker.addPoint(inaccuratePoint, currentTimeMs = now)

        assertFalse("Inaccurate points must be rejected", accepted)
        assertEquals(0, tracker.size())
    }

    // ─── 4. Stale Points Rejected ────────────────────────────────────────────

    @Test
    fun `stale point older than staleness threshold is rejected`() {
        val stalePoint = RideLocationPoint(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 10.0f,
            timestampMs = now - 65_000L // 65 seconds old, threshold is 60s
        )

        val accepted = tracker.addPoint(stalePoint, currentTimeMs = now)

        assertFalse("Stale points must be rejected", accepted)
        assertEquals(0, tracker.size())
    }

    @Test
    fun `retro-temporal point chronologically older than last accepted point is rejected`() {
        val point1 = RideLocationPoint(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 10f, timestampMs = now)
        assertTrue(tracker.addPoint(point1, currentTimeMs = now))

        // New point with a timestamp earlier than point1
        val retroPoint = RideLocationPoint(
            latitude = 12.9750,
            longitude = 77.5980,
            accuracyMeters = 10f,
            timestampMs = now - 5_000L
        )

        assertFalse("Retro-temporal point must be rejected", tracker.addPoint(retroPoint, currentTimeMs = now))
        assertEquals(1, tracker.size())
    }

    // ─── 5. Duplicate / Near-Duplicate Stationary Filter ─────────────────────

    @Test
    fun `near duplicate point with insignificant movement is ignored`() {
        val startPoint = RideLocationPoint(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = now)
        assertTrue(tracker.addPoint(startPoint, currentTimeMs = now))

        // Tiny sub-meter displacement (~0.5m)
        val jitterPoint = RideLocationPoint(
            latitude = 12.971604,
            longitude = 77.594604,
            accuracyMeters = 5f,
            timestampMs = now + 5000L
        )

        assertFalse("Sub-3m stationary jitter must be ignored", tracker.addPoint(jitterPoint, currentTimeMs = now + 5000L))
        assertEquals(1, tracker.size())
    }

    @Test
    fun `meaningful movement is accepted and appended`() {
        val startPoint = RideLocationPoint(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = now)
        assertTrue(tracker.addPoint(startPoint, currentTimeMs = now))

        // Distinct motorcycle movement (~100m)
        val nextPoint = RideLocationPoint(
            latitude = 12.9725,
            longitude = 77.5950,
            accuracyMeters = 5f,
            timestampMs = now + 5000L
        )

        assertTrue("Meaningful movement must be accepted", tracker.addPoint(nextPoint, currentTimeMs = now + 5000L))
        assertEquals(2, tracker.size())
    }

    @Test
    fun `impossible location jump is rejected and does not replace the last good point`() {
        val start = RideLocationPoint(12.9716, 77.5946, 5f, now)
        val impossibleJump = RideLocationPoint(12.9916, 77.5946, 5f, now + 5_000L)
        assertTrue(tracker.addPoint(start, currentTimeMs = now))
        assertFalse(tracker.addPoint(impossibleJump, currentTimeMs = now + 5_000L))
        assertEquals(listOf(start), tracker.getCurrentPath())
    }

    @Test
    fun `non-monotonic and future-dated fixes are rejected`() {
        val start = RideLocationPoint(12.9716, 77.5946, 5f, now)
        assertTrue(tracker.addPoint(start, currentTimeMs = now))
        assertFalse(tracker.addPoint(RideLocationPoint(12.9720, 77.5946, 5f, now), currentTimeMs = now))
        assertFalse(tracker.addPoint(RideLocationPoint(12.9720, 77.5946, 5f, now + 10_000L), currentTimeMs = now))
        assertEquals(listOf(start), tracker.getCurrentPath())
    }

    // ─── 6. Bounded Path Size ────────────────────────────────────────────────

    @Test
    fun `path size does not exceed maxPoints and drops oldest points`() {
        // maxPoints is configured to 10 in setUp()
        for (i in 0 until 15) {
            val point = RideLocationPoint(
                latitude = 12.0 + (i * 0.001),
                longitude = 77.0 + (i * 0.001),
                accuracyMeters = 5f,
                timestampMs = now + (i * 5000L)
            )
            tracker.addPoint(point, currentTimeMs = now + (i * 5000L))
        }

        assertEquals("Path size must remain bounded at maxPoints", 10, tracker.size())
        // Oldest points 0..4 dropped; first element should be point index 5
        assertEquals(12.005, tracker.getCurrentPath().first().latitude, 0.0001)
    }

    // ─── 7. Path Reset on New Ride ───────────────────────────────────────────

    @Test
    fun `reset clears all recorded points`() {
        val point = RideLocationPoint(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = now)
        tracker.addPoint(point, currentTimeMs = now)
        assertEquals(1, tracker.size())

        tracker.reset()

        assertEquals(0, tracker.size())
        assertTrue(tracker.getCurrentPath().isEmpty())
    }

    // ─── 8. Path Cleanup on Ride Stop ────────────────────────────────────────

    @Test
    fun `clear clears all recorded points on ride stop`() {
        val point = RideLocationPoint(latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = now)
        tracker.addPoint(point, currentTimeMs = now)
        assertEquals(1, tracker.size())

        tracker.clear()

        assertEquals(0, tracker.size())
        assertTrue(tracker.getCurrentPath().isEmpty())
    }

    // ─── 9. Add from State Helper ────────────────────────────────────────────

    @Test
    fun `addFromState converts RideLocationState Available correctly`() {
        val available = RideLocationState.Available(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 8.0f,
            timestampMs = now,
            speedMps = 15.0f,
            bearingDegrees = 90.0f
        )

        assertTrue(tracker.addFromState(available, currentTimeMs = now))
        assertEquals(1, tracker.size())
        val recorded = tracker.getCurrentPath().first()
        assertEquals(12.9716, recorded.latitude, 0.0001)
        assertEquals(77.5946, recorded.longitude, 0.0001)
        assertEquals(8.0f, recorded.accuracyMeters)
    }
    @Test
    fun `accuracy uncertainty rejects stationary drift even above minimum displacement`() {
        val start = RideLocationPoint(12.9716, 77.5946, 10f, now)
        val drift = RideLocationPoint(12.97172, 77.5946, 10f, now + 5_000L)
        assertTrue(tracker.addPoint(start, currentTimeMs = now))
        assertFalse(tracker.addPoint(drift, currentTimeMs = now + 5_000L))
        assertEquals(1, tracker.size())
    }

    @Test
    fun `accurate movement at high motorcycle speed remains accepted`() {
        val start = RideLocationPoint(12.9716, 77.5946, 3f, now)
        val fast = RideLocationPoint(12.9728, 77.5946, 3f, now + 2_000L)
        assertTrue(tracker.addPoint(start, currentTimeMs = now))
        assertTrue(tracker.addPoint(fast, currentTimeMs = now + 2_000L))
    }}
