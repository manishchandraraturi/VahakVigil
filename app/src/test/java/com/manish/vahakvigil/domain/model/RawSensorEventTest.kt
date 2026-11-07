package com.manish.vahakvigil.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RawSensorEventTest {
    @Test
    fun `event represents one actual sensor sample with raw units and timestamp`() {
        val accelerometer = RawSensorEvent(
            type = RawSensorType.ACCELEROMETER,
            timestampNanos = 987_654_321L,
            timestampEpochMillis = 1_700_000_000_987L,
            x = 1.25f,
            y = -2.5f,
            z = 9.81f,
            accuracy = 3
        )
        val gyroscope = RawSensorEvent(
            type = RawSensorType.GYROSCOPE,
            timestampNanos = 987_654_322L,
            timestampEpochMillis = 1_700_000_000_987L,
            x = 0.1f,
            y = 0.2f,
            z = 0.3f,
            accuracy = 2
        )

        assertEquals(RawSensorType.ACCELEROMETER, accelerometer.type)
        assertEquals(987_654_321L, accelerometer.timestampNanos)
        assertEquals(1.25f, accelerometer.x)
        assertEquals(RawSensorType.GYROSCOPE, gyroscope.type)
        assertEquals(987_654_322L, gyroscope.timestampNanos)
        assertEquals(0.3f, gyroscope.z)
    }
}
