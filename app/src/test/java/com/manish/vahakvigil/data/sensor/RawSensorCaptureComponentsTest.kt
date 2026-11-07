package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSensorCaptureComponentsTest {
    @Test
    fun `monotonic hardware timestamp converts using sampled epoch offset`() {
        val converter = SensorTimestampConverter.fromClockSample(
            epochMillis = 1_700_000_010_000L,
            elapsedRealtimeNanos = 10_000_000_000L
        )

        assertEquals(1_700_000_010_125L, converter.toEpochMillis(10_125_000_000L))
    }

    @Test
    fun `buffer preserves event ordering and drops incoming events when full`() = runTest {
        val buffer = BoundedRawSensorEventBuffer(capacity = 2)
        val first = event(1L, RawSensorType.ACCELEROMETER)
        val second = event(2L, RawSensorType.GYROSCOPE)
        val overflow = event(3L, RawSensorType.ACCELEROMETER)

        assertTrue(buffer.tryPublish(first))
        assertTrue(buffer.tryPublish(second))
        assertFalse(buffer.tryPublish(overflow))
        assertEquals(1L, buffer.droppedEventCount)

        val received = buffer.events.take(2).toList()
        assertEquals(listOf(first, second), received)
    }

    @Test
    fun `buffer counts each rejected event`() {
        val buffer = BoundedRawSensorEventBuffer(capacity = 1)

        assertTrue(buffer.tryPublish(event(1L, RawSensorType.ACCELEROMETER)))
        assertFalse(buffer.tryPublish(event(2L, RawSensorType.GYROSCOPE)))
        assertFalse(buffer.tryPublish(event(3L, RawSensorType.ACCELEROMETER)))

        assertEquals(2L, buffer.droppedEventCount)
    }

    private fun event(timestamp: Long, type: RawSensorType) = RawSensorEvent(
        type = type,
        timestampNanos = timestamp,
        timestampEpochMillis = timestamp,
        x = timestamp.toFloat(),
        y = 0f,
        z = 0f,
        accuracy = 3
    )
}
