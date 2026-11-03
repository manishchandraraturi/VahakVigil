package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionEventType
import com.manish.vahakvigil.domain.model.SensorReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [MotionAnalyzer].
 *
 * All tests run on a plain JVM — no Android instrumentation required.
 *
 * The config uses explicit threshold values so test intent is clear and
 * independent of any future default value changes in [MotionDetectionConfig].
 */
class MotionAnalyzerTest {

    // Explicit thresholds: acceleration ≥ 20 m/s², gyroscope ≥ 3 rad/s
    private val config = MotionDetectionConfig(
        accelerationThresholdMs2 = 20f,
        gyroscopeThresholdRads = 3f
    )
    private val analyzer = MotionAnalyzer(config)

    // ─── Helper builders ────────────────────────────────────────────────────

    /** Builds a [SensorReading] with explicit magnitudes and zero individual axes. */
    private fun reading(accMagnitude: Float, gyroMagnitude: Float): SensorReading {
        // Back-calculate ax so that sqrt(ax²) == accMagnitude (with ay=az=0)
        // and gx so that sqrt(gx²) == gyroMagnitude (with gy=gz=0)
        return SensorReading(
            timestamp = 1_000L,
            ax = accMagnitude,
            ay = 0f,
            az = 0f,
            gx = gyroMagnitude,
            gy = 0f,
            gz = 0f,
            accelerationMagnitude = accMagnitude,
            gyroscopeMagnitude = gyroMagnitude
        )
    }

    // ─── Scenario 1: Both below threshold → null ─────────────────────────────

    @Test
    fun `returns null when both magnitudes are below threshold`() {
        val result = analyzer.analyze(reading(accMagnitude = 5f, gyroMagnitude = 1f))
        assertNull("Normal motion should produce no event", result)
    }

    // ─── Scenario 2: Both exactly at threshold → COMBINED_IMPACT ────────────

    @Test
    fun `returns COMBINED_IMPACT when both magnitudes exactly meet threshold`() {
        val result = analyzer.analyze(reading(accMagnitude = 20f, gyroMagnitude = 3f))
        assertNotNull(result)
        assertEquals(MotionEventType.COMBINED_IMPACT, result!!.eventType)
    }

    // ─── Scenario 3: Both above threshold → COMBINED_IMPACT ─────────────────

    @Test
    fun `returns COMBINED_IMPACT when both magnitudes exceed threshold`() {
        val result = analyzer.analyze(reading(accMagnitude = 35f, gyroMagnitude = 7f))
        assertNotNull(result)
        assertEquals(MotionEventType.COMBINED_IMPACT, result!!.eventType)
    }

    // ─── Scenario 4: Only acceleration above threshold → HIGH_ACCELERATION ───

    @Test
    fun `returns HIGH_ACCELERATION when only acceleration exceeds threshold`() {
        val result = analyzer.analyze(reading(accMagnitude = 25f, gyroMagnitude = 1f))
        assertNotNull(result)
        assertEquals(MotionEventType.HIGH_ACCELERATION, result!!.eventType)
    }

    // ─── Scenario 5: Only gyroscope above threshold → HIGH_GYROSCOPE ─────────

    @Test
    fun `returns HIGH_GYROSCOPE when only gyroscope exceeds threshold`() {
        val result = analyzer.analyze(reading(accMagnitude = 5f, gyroMagnitude = 5f))
        assertNotNull(result)
        assertEquals(MotionEventType.HIGH_GYROSCOPE, result!!.eventType)
    }

    // ─── Scenario 6: MotionEvent carries correct timestamp ───────────────────

    @Test
    fun `produced MotionEvent carries the timestamp from the SensorReading`() {
        val input = SensorReading(
            timestamp = 9_876_543L,
            ax = 30f, ay = 0f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f,
            gyroscopeMagnitude = 0f
        )
        val result = analyzer.analyze(input)
        assertNotNull(result)
        assertEquals(9_876_543L, result!!.timestamp)
    }

    // ─── Scenario 7: MotionEvent carries correct magnitudes ──────────────────

    @Test
    fun `produced MotionEvent carries the exact magnitudes from the SensorReading`() {
        val input = reading(accMagnitude = 22.5f, gyroMagnitude = 4.1f)
        val result = analyzer.analyze(input)
        assertNotNull(result)
        assertEquals(22.5f, result!!.accelerationMagnitude, 0.001f)
        assertEquals(4.1f, result.gyroscopeMagnitude, 0.001f)
    }

    // ─── Scenario 8: Acceleration exactly below threshold → null ─────────────

    @Test
    fun `returns null when acceleration is just below threshold and gyroscope is zero`() {
        val result = analyzer.analyze(reading(accMagnitude = 19.99f, gyroMagnitude = 0f))
        assertNull("Acceleration just below threshold should produce no event", result)
    }

    // ─── Scenario 9: MotionEvent carries raw axis values ─────────────────────

    @Test
    fun `produced MotionEvent preserves raw axis values from the SensorReading`() {
        val input = SensorReading(
            timestamp = 100L,
            ax = 10f, ay = 5f, az = 15f,
            gx = 2f, gy = 1f, gz = 1f,
            accelerationMagnitude = 25f,
            gyroscopeMagnitude = 0.5f
        )
        val result = analyzer.analyze(input)
        // Only acceleration threshold exceeded
        assertNotNull(result)
        assertEquals(MotionEventType.HIGH_ACCELERATION, result!!.eventType)
        assertEquals(10f, result.ax, 0.001f)
        assertEquals(5f, result.ay, 0.001f)
        assertEquals(15f, result.az, 0.001f)
        assertEquals(2f, result.gx, 0.001f)
        assertEquals(1f, result.gy, 0.001f)
        assertEquals(1f, result.gz, 0.001f)
    }
}
