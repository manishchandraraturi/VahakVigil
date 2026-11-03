package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionWindow
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.QualifiedIncident
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [PostImpactMotionAnalyzer].
 *
 * All tests use deterministic inputs and explicit timestamps.
 * No real clocks, delays, or Android dependencies are used.
 */
class PostImpactMotionAnalyzerTest {

    private val defaultConfig = PostImpactMotionConfig(
        postImpactWindowMillis = 5000L,
        stillnessAccelerationThreshold = 10.5f,
        stillnessGyroscopeThreshold = 0.5f,
        requiredStillnessDurationMillis = 2000L
    )
    private val analyzer = PostImpactMotionAnalyzer(defaultConfig)

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun createIncident(
        startMs: Long = 1000L,
        endMs: Long = 2000L,
        score: Float = 0.75f
    ): QualifiedIncident {
        val temporalEvent = TemporalMotionEvent(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            windowStartMs = startMs,
            windowEndMs = endMs,
            peakAccelerationMagnitude = 35f,
            peakGyroscopeMagnitude = 4f,
            readingCount = 5
        )
        val candidate = CandidateAccidentEvent(
            windowStartMs = startMs,
            windowEndMs = endMs,
            confidenceScore = ConfidenceScore.fromRaw(score),
            temporalEvent = temporalEvent,
            readingCount = 5
        )
        return QualifiedIncident(
            windowStartMs = startMs,
            windowEndMs = endMs,
            qualifyingConfidenceScore = ConfidenceScore.fromRaw(score),
            qualifyingWindowCount = 1,
            candidateEvents = listOf(candidate)
        )
    }

    private fun reading(
        timestamp: Long,
        accMagnitude: Float = 9.8f, // Earth gravity placeholder (<= 10.5)
        gyroMagnitude: Float = 0.1f // Near zero angular velocity (<= 0.5)
    ) = SensorReading(
        timestamp = timestamp,
        ax = accMagnitude,
        ay = 0f,
        az = 0f,
        gx = gyroMagnitude,
        gy = 0f,
        gz = 0f,
        accelerationMagnitude = accMagnitude,
        gyroscopeMagnitude = gyroMagnitude
    )

    // ─── 1. No QualifiedIncident → post-impact analysis does not begin ─────────

    @Test
    fun `no QualifiedIncident returns OBSERVING with zeroed values`() {
        val window = PostImpactMotionWindow(listOf(reading(3000L)))
        val result = analyzer.analyze(null, window)

        assertEquals(PostImpactStatus.OBSERVING, result.status)
        assertEquals(0, result.readingCount)
        assertEquals(0L, result.observationStartMs)
        assertEquals(0L, result.observationEndMs)
        assertEquals(0L, result.stillnessDurationMs)
    }

    // ─── 2. Empty post-impact window → OBSERVING ──────────────────────────────

    @Test
    fun `empty post-impact window produces OBSERVING status`() {
        val incident = createIncident()
        val emptyWindow = PostImpactMotionWindow(emptyList())
        val result = analyzer.analyze(incident, emptyWindow)

        assertEquals(PostImpactStatus.OBSERVING, result.status)
        assertEquals(0, result.readingCount)
        assertEquals(0L, result.stillnessDurationMs)
    }

    // ─── 3. Reading before incident timestamp is ignored ──────────────────────

    @Test
    fun `readings before incident end timestamp are ignored`() {
        val incident = createIncident(endMs = 2000L)
        // 1500L is before incident end (2000L), 2000L is equal to incident end
        val preIncidentReadings = listOf(
            reading(timestamp = 1500L, accMagnitude = 25f, gyroMagnitude = 5f),
            reading(timestamp = 2000L, accMagnitude = 25f, gyroMagnitude = 5f)
        )
        val window = PostImpactMotionWindow(preIncidentReadings)
        val result = analyzer.analyze(incident, window)

        assertEquals("Pre-incident readings must be ignored", 0, result.readingCount)
        assertEquals(PostImpactStatus.OBSERVING, result.status)
    }

    // ─── 4. Reading after incident timestamp enters observation window ────────

    @Test
    fun `readings after incident end timestamp enter observation window`() {
        val incident = createIncident(endMs = 2000L)
        val validReading = reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f)
        val window = PostImpactMotionWindow(listOf(validReading))
        val result = analyzer.analyze(incident, window)

        assertEquals(1, result.readingCount)
        assertEquals(2500L, result.observationStartMs)
        assertEquals(2500L, result.observationEndMs)
    }

    // ─── 5. One still reading does not qualify as sustained stillness ─────────

    @Test
    fun `one still reading does not qualify as sustained stillness`() {
        val incident = createIncident(endMs = 2000L)
        val singleReading = reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f)
        val window = PostImpactMotionWindow(listOf(singleReading))
        val result = analyzer.analyze(incident, window)

        // Single reading has duration 0 ms < 2000 ms required
        assertEquals(PostImpactStatus.OBSERVING, result.status)
        assertEquals(0L, result.stillnessDurationMs)
    }

    // ─── 6. Multiple still readings meeting required duration → STILL ─────────

    @Test
    fun `multiple still readings spanning required duration produce STILL`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 3500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 4500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f) // 4500 - 2500 = 2000 ms
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.STILL, result.status)
        assertEquals(2000L, result.stillnessDurationMs)
        assertEquals(3, result.readingCount)
    }

    // ─── 7. Stillness duration just below required duration → not STILL ───────

    @Test
    fun `stillness duration just below required duration produces OBSERVING`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 4499L, accMagnitude = 9.8f, gyroMagnitude = 0.1f) // 1999 ms < 2000 ms
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertNotEquals(PostImpactStatus.STILL, result.status)
        assertEquals(PostImpactStatus.OBSERVING, result.status)
        assertEquals(1999L, result.stillnessDurationMs)
    }

    // ─── 8. Exact required stillness duration → consistent expected result ────

    @Test
    fun `exact required stillness duration produces STILL`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 4500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f) // exactly 2000 ms
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.STILL, result.status)
        assertEquals(2000L, result.stillnessDurationMs)
    }

    // ─── 9. Motion above acceleration threshold breaks stillness streak ───────

    @Test
    fun `motion above acceleration threshold breaks stillness streak and results in MOVING`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 3500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            // High acceleration breaks stillness
            reading(timestamp = 4000L, accMagnitude = 15.0f, gyroMagnitude = 0.1f)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.MOVING, result.status)
        assertEquals(0L, result.stillnessDurationMs)
    }

    // ─── 10. Motion above gyroscope threshold breaks stillness streak ─────────

    @Test
    fun `motion above gyroscope threshold breaks stillness streak and results in MOVING`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 3500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            // High gyroscope rotation breaks stillness
            reading(timestamp = 4000L, accMagnitude = 9.8f, gyroMagnitude = 1.5f)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.MOVING, result.status)
        assertEquals(0L, result.stillnessDurationMs)
    }

    // ─── 11. Both acc and gyro must be below thresholds for still ─────────────

    @Test
    fun `both acceleration and gyroscope must be below thresholds for still`() {
        val incident = createIncident(endMs = 2000L)

        // Case A: low acc, high gyro -> NOT still
        val highGyro = PostImpactMotionWindow(listOf(reading(2500L, accMagnitude = 9.8f, gyroMagnitude = 1.0f)))
        assertEquals(PostImpactStatus.MOVING, analyzer.analyze(incident, highGyro).status)

        // Case B: high acc, low gyro -> NOT still
        val highAcc = PostImpactMotionWindow(listOf(reading(2500L, accMagnitude = 14.0f, gyroMagnitude = 0.1f)))
        assertEquals(PostImpactStatus.MOVING, analyzer.analyze(incident, highAcc).status)

        // Case C: both low -> Still (OBSERVING on single reading)
        val bothLow = PostImpactMotionWindow(listOf(reading(2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f)))
        assertEquals(PostImpactStatus.OBSERVING, analyzer.analyze(incident, bothLow).status)
    }

    // ─── 12. Readings outside post-impact observation window are ignored ──────

    @Test
    fun `readings outside post-impact observation window are ignored`() {
        // incidentEnd = 2000L, postImpactWindowMillis = 5000L -> window limit is 7000L
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 7500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f) // > 7000L ignored
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(1, result.readingCount)
        assertEquals(2500L, result.observationStartMs)
        assertEquals(2500L, result.observationEndMs)
    }

    // ─── 13. Moving readings produce MOVING where appropriate ─────────────────

    @Test
    fun `consistent moving readings produce MOVING status`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 18f, gyroMagnitude = 2.0f),
            reading(timestamp = 3000L, accMagnitude = 16f, gyroMagnitude = 1.8f),
            reading(timestamp = 3500L, accMagnitude = 17f, gyroMagnitude = 2.2f)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.MOVING, result.status)
        assertEquals(0L, result.stillnessDurationMs)
    }

    // ─── 14. Still -> moving -> still resets and then rebuilds duration ───────

    @Test
    fun `still then moving then still resets and rebuilds stillness duration`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            // Streak 1 (t=2500..3500: 1000ms duration)
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 3500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            // Interruption (t=3800: moving)
            reading(timestamp = 3800L, accMagnitude = 20f, gyroMagnitude = 3f),
            // Streak 2 (t=4000..6000: 2000ms duration -> STILL)
            reading(timestamp = 4000L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 5000L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 6000L, accMagnitude = 9.8f, gyroMagnitude = 0.1f)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.STILL, result.status)
        // Duration of current streak is 6000 - 4000 = 2000 ms
        assertEquals(2000L, result.stillnessDurationMs)
    }

    // ─── 15. Out-of-order timestamps are handled safely ───────────────────────

    @Test
    fun `out-of-order timestamps are safely ignored and do not corrupt duration`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 4500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            // Out of order: timestamp jumps backwards
            reading(timestamp = 3000L, accMagnitude = 9.8f, gyroMagnitude = 0.1f)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        // Chronological filtering keeps 2500L and 4500L, drops 3000L
        assertEquals(2, result.readingCount)
        assertEquals(2000L, result.stillnessDurationMs)
        assertEquals(PostImpactStatus.STILL, result.status)
    }

    // ─── 16. Equal timestamps do not create artificial duration ───────────────

    @Test
    fun `equal timestamps do not create artificial stillness duration`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f),
            reading(timestamp = 2500L, accMagnitude = 9.8f, gyroMagnitude = 0.1f)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        // Duration remains 0 ms
        assertEquals(0L, result.stillnessDurationMs)
        assertEquals(PostImpactStatus.OBSERVING, result.status)
    }

    // ─── 17. Extreme sensor values do not produce invalid results ─────────────

    @Test
    fun `extreme and NaN sensor values are safely handled`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = Float.MAX_VALUE, gyroMagnitude = Float.MAX_VALUE),
            reading(timestamp = 3000L, accMagnitude = Float.NaN, gyroMagnitude = Float.POSITIVE_INFINITY)
        )
        val window = PostImpactMotionWindow(readings)
        val result = analyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.MOVING, result.status)
        assertTrue(!result.peakAccelerationMagnitude.isNaN())
        assertTrue(!result.peakGyroscopeMagnitude.isNaN())
    }

    // ─── 18. Configuration values affect classification ───────────────────────

    @Test
    fun `custom config thresholds alter classification behavior`() {
        val incident = createIncident(endMs = 2000L)
        val readings = listOf(
            reading(timestamp = 2500L, accMagnitude = 12.0f, gyroMagnitude = 0.3f),
            reading(timestamp = 3500L, accMagnitude = 12.0f, gyroMagnitude = 0.3f)
        )
        val window = PostImpactMotionWindow(readings)

        // With default config (acc threshold 10.5), acc 12.0 is MOVING
        val defaultResult = analyzer.analyze(incident, window)
        assertEquals(PostImpactStatus.MOVING, defaultResult.status)

        // With lenient config (acc threshold 15.0, required duration 1000ms)
        val lenientConfig = PostImpactMotionConfig(
            postImpactWindowMillis = 5000L,
            stillnessAccelerationThreshold = 15.0f,
            stillnessGyroscopeThreshold = 0.5f,
            requiredStillnessDurationMillis = 1000L
        )
        val lenientAnalyzer = PostImpactMotionAnalyzer(lenientConfig)
        val lenientResult = lenientAnalyzer.analyze(incident, window)

        assertEquals(PostImpactStatus.STILL, lenientResult.status)
    }
}
