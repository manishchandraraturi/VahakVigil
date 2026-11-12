package com.manish.vahakvigil.domain.analysis

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.SensorStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorRecordingAnalyzerTest {
    @Test fun magnitudeAndSamplingStatisticsUseRecordedAxesAndPositiveIntervals() {
        val accumulator = SensorStreamAccumulator(SensorStreamType.ACCELEROMETER)
        accumulator.add(event(1_000_000_000, 3f, 4f, 0f))
        accumulator.add(event(1_010_000_000, 0f, 0f, 2f))
        accumulator.add(event(1_030_000_000, 0f, 0f, 1f))

        val analysis = accumulator.finish()!!
        assertEquals(3, analysis.eventCount)
        assertEquals(8.0 / 3.0, analysis.averageMagnitude!!, 0.0001)
        assertEquals(5.0, analysis.peakMagnitude!!, 0.0001)
        assertEquals(10.0, analysis.minimumSampleIntervalMillis!!, 0.0001)
        assertEquals(20.0, analysis.maximumSampleIntervalMillis!!, 0.0001)
        assertEquals(15.0, analysis.medianSampleIntervalMillisApprox!!, 0.0001)
        assertEquals(66.6667, analysis.sampleFrequencyHzApprox!!, 0.001)
    }

    @Test fun duplicateNonMonotonicMissingAccuracyAndNonFiniteValuesAreCounted() {
        val accumulator = SensorStreamAccumulator(SensorStreamType.GYROSCOPE)
        accumulator.add(event(100, 1f, 2f, 3f, accuracy = 0, type = RawSensorType.GYROSCOPE))
        accumulator.add(event(100, Float.NaN, 0f, 0f, type = RawSensorType.GYROSCOPE))
        accumulator.add(event(90, 1f, 1f, 1f, type = RawSensorType.GYROSCOPE))

        val analysis = accumulator.finish()!!
        assertEquals(1, analysis.duplicateTimestampCount)
        assertEquals(1, analysis.nonMonotonicTimestampCount)
        assertEquals(1, analysis.missingAccuracyCount)
        assertEquals(1, analysis.nonFiniteValueCount)
        assertEquals(90, analysis.minimumTimestampNanos)
        assertEquals(100, analysis.maximumTimestampNanos)
    }

    @Test fun timelineIsBoundedPreservesAxisExtremaAndSelectsAnActualEvent() {
        val timeline = SensorTimelineAccumulator(SensorStreamType.ACCELEROMETER, 0, 9, 10, maxBuckets = 3)
        (0..9).forEach { index -> timeline.add(event(index.toLong(), index.toFloat(), (-index).toFloat(), 0f)) }

        val result = timeline.finish()
        assertEquals(3, result.size)
        assertEquals(0f, result.first().minX)
        assertEquals(2f, result.first().maxX)
        assertEquals(7f, result.last().selectedSample.x)
        assertEquals(7L, result.last().selectedSample.timestampNanos)
    }

    @Test fun medianEstimatorUsesExactMedianForSmallSamplesAndRemainsBoundedForLargeSamples() {
        val estimator = P2MedianEstimator()
        listOf(8.0, 2.0, 6.0, 4.0).forEach(estimator::add)
        assertEquals(5.0, estimator.estimate()!!, 0.0)

        val larger = P2MedianEstimator()
        (1..100_000).forEach { larger.add(it.toDouble()) }
        assertEquals(50_000.0, larger.estimate()!!, 500.0)
    }

    @Test fun emptyStreamHasNoStatistics() {
        assertNull(SensorStreamAccumulator(SensorStreamType.ACCELEROMETER).finish())
    }

    private fun event(timestamp: Long, x: Float, y: Float, z: Float, accuracy: Int = 3, type: RawSensorType = RawSensorType.ACCELEROMETER) = RawSensorEvent(
        type = type,
        timestampNanos = timestamp,
        timestampEpochMillis = timestamp / 1_000_000L + 1_700_000_000_000L,
        x = x,
        y = y,
        z = z,
        accuracy = accuracy
    )
}
