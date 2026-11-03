package com.manish.vahakvigil.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorReadingTest {

    @Test
    fun `calculateMagnitude calculates 3D Euclidean magnitude correctly`() {
        val magnitude340 = SensorReading.calculateMagnitude(3f, 4f, 0f)
        assertEquals(5.0f, magnitude340, 0.001f)

        val magnitude122 = SensorReading.calculateMagnitude(1f, 2f, 2f)
        assertEquals(3.0f, magnitude122, 0.001f)

        val magnitude0 = SensorReading.calculateMagnitude(0f, 0f, 0f)
        assertEquals(0.0f, magnitude0, 0.001f)

        val gravityApprox = SensorReading.calculateMagnitude(0f, 9.8f, 0f)
        assertEquals(9.8f, gravityApprox, 0.001f)
    }

    @Test
    fun `SensorReading computes acceleration and gyroscope magnitudes on construction`() {
        val reading = SensorReading(
            timestamp = 1_000_000L,
            ax = 0f,
            ay = 6f,
            az = 8f,
            gx = 1f,
            gy = 2f,
            gz = 2f
        )

        assertEquals(10.0f, reading.accelerationMagnitude, 0.001f)
        assertEquals(3.0f, reading.gyroscopeMagnitude, 0.001f)
        assertEquals(1_000_000L, reading.timestamp)
    }

    @Test
    fun `SensorCapabilities reports isSupported based on accelerometer availability`() {
        val fullSensors = SensorCapabilities(hasAccelerometer = true, hasGyroscope = true)
        assertTrue(fullSensors.isSupported)

        val accelOnly = SensorCapabilities(hasAccelerometer = true, hasGyroscope = false)
        assertTrue(accelOnly.isSupported)

        val noSensors = SensorCapabilities(hasAccelerometer = false, hasGyroscope = false)
        assertTrue(!noSensors.isSupported)

        val gyroOnly = SensorCapabilities(hasAccelerometer = false, hasGyroscope = true)
        assertTrue(!gyroOnly.isSupported)
    }
}
