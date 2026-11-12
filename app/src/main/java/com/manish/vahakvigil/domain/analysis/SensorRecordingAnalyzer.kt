package com.manish.vahakvigil.domain.analysis

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.SensorStreamAnalysis
import com.manish.vahakvigil.domain.model.SensorStreamType
import com.manish.vahakvigil.domain.model.SensorTimelineBucket
import com.manish.vahakvigil.domain.model.SensorTimelineSample
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/** Constant-memory median estimator used for observed sensor sampling intervals. */
internal class P2MedianEstimator {
    private val initial = mutableListOf<Double>()
    private val heights = DoubleArray(5)
    private val positions = IntArray(5)
    private val desiredPositions = DoubleArray(5)
    private val desiredIncrements = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)
    private var count = 0L

    fun add(value: Double) {
        if (!value.isFinite()) return
        if (count < 5) {
            initial += value
            count++
            if (count == 5L) {
                initial.sort()
                for (i in 0..4) {
                    heights[i] = initial[i]
                    positions[i] = i + 1
                    desiredPositions[i] = (i + 1).toDouble()
                }
            }
            return
        }

        count++
        val cell = when {
            value < heights[0] -> { heights[0] = value; 0 }
            value >= heights[4] -> { heights[4] = value; 3 }
            else -> (0..3).first { value >= heights[it] && value < heights[it + 1] }
        }
        for (i in cell + 1..4) positions[i]++
        for (i in 0..4) desiredPositions[i] += desiredIncrements[i]
        for (i in 1..3) {
            val displacement = desiredPositions[i] - positions[i]
            val direction = when {
                displacement >= 1.0 && positions[i + 1] - positions[i] > 1 -> 1
                displacement <= -1.0 && positions[i - 1] - positions[i] < -1 -> -1
                else -> 0
            }
            if (direction == 0) continue
            val proposed = heights[i] + direction / (positions[i + 1] - positions[i - 1]).toDouble() * (
                (positions[i] - positions[i - 1] + direction) * (heights[i + 1] - heights[i]) / (positions[i + 1] - positions[i]) +
                    (positions[i + 1] - positions[i] - direction) * (heights[i] - heights[i - 1]) / (positions[i] - positions[i - 1])
                )
            if (proposed > heights[i - 1] && proposed < heights[i + 1]) {
                heights[i] = proposed
            } else {
                heights[i] += direction * (heights[i + direction] - heights[i]) / (positions[i + direction] - positions[i]).toDouble()
            }
            positions[i] += direction
        }
    }

    fun estimate(): Double? = when {
        count == 0L -> null
        count < 5L -> initial.sorted().let { sorted ->
            if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        else -> heights[2]
    }
}

class SensorStreamAccumulator(private val type: SensorStreamType) {
    var count = 0L
        private set
    private var firstNanos: Long? = null
    private var lastNanos: Long? = null
    private var minNanos: Long? = null
    private var maxNanos: Long? = null
    private var firstEpoch: Long? = null
    private var lastEpoch: Long? = null
    private var sumMagnitude = 0.0
    private var magnitudeCount = 0L
    private var peakMagnitude: Double? = null
    private var positiveIntervalSumNanos = 0.0
    private var positiveIntervalCount = 0L
    private var minIntervalNanos: Long? = null
    private var maxIntervalNanos: Long? = null
    private val medianInterval = P2MedianEstimator()
    var duplicateCount = 0L
        private set
    var nonMonotonicCount = 0L
        private set
    var missingAccuracyCount = 0L
        private set
    var nonFiniteValueCount = 0L
        private set
    private var minEpochOffset: Long? = null
    private var maxEpochOffset: Long? = null

    fun add(event: RawSensorEvent) {
        require(event.type.name == type.name)
        count++
        if (firstNanos == null) {
            firstNanos = event.timestampNanos
            firstEpoch = event.timestampEpochMillis
        }
        lastNanos?.let { previous ->
            val interval = event.timestampNanos - previous
            if (interval == 0L) duplicateCount++
            if (interval < 0L) nonMonotonicCount++
            if (interval > 0L) {
                medianInterval.add(interval.toDouble())
                positiveIntervalSumNanos += interval.toDouble()
                positiveIntervalCount++
                minIntervalNanos = minIntervalNanos?.let { minOf(it, interval) } ?: interval
                maxIntervalNanos = maxIntervalNanos?.let { maxOf(it, interval) } ?: interval
            }
        }
        lastNanos = event.timestampNanos
        minNanos = minNanos?.let { minOf(it, event.timestampNanos) } ?: event.timestampNanos
        maxNanos = maxNanos?.let { maxOf(it, event.timestampNanos) } ?: event.timestampNanos
        lastEpoch = event.timestampEpochMillis
        val offset = event.timestampEpochMillis - event.timestampNanos / 1_000_000L
        minEpochOffset = minEpochOffset?.let { minOf(it, offset) } ?: offset
        maxEpochOffset = maxEpochOffset?.let { maxOf(it, offset) } ?: offset
        if (event.accuracy <= 0) missingAccuracyCount++
        val magnitude = magnitude(event.x, event.y, event.z)
        if (magnitude == null) {
            nonFiniteValueCount++
        } else {
            sumMagnitude += magnitude
            magnitudeCount++
            peakMagnitude = peakMagnitude?.let { maxOf(it, magnitude) } ?: magnitude
        }
    }

    fun finish(irregularGapCount: Long = 0L, timeline: List<SensorTimelineBucket> = emptyList()): SensorStreamAnalysis? {
        if (count == 0L) return null
        val median = medianInterval.estimate()
        val intervalMean = (positiveIntervalSumNanos / positiveIntervalCount).takeIf { positiveIntervalCount > 0L && it.isFinite() && it > 0.0 }
        val first = firstNanos!!
        val last = lastNanos!!
        return SensorStreamAnalysis(
            type = type,
            eventCount = count,
            firstTimestampNanos = first,
            lastTimestampNanos = last,
            minimumTimestampNanos = minNanos!!,
            maximumTimestampNanos = maxNanos!!,
            firstTimestampEpochMillis = firstEpoch!!,
            lastTimestampEpochMillis = lastEpoch!!,
            averageMagnitude = (sumMagnitude / magnitudeCount).takeIf { magnitudeCount > 0L },
            peakMagnitude = peakMagnitude,
            minimumSampleIntervalMillis = minIntervalNanos?.div(1_000_000.0),
            medianSampleIntervalMillisApprox = median?.div(1_000_000.0),
            maximumSampleIntervalMillis = maxIntervalNanos?.div(1_000_000.0),
            sampleFrequencyHzApprox = intervalMean?.let { 1_000_000_000.0 / it },
            duplicateTimestampCount = duplicateCount,
            nonMonotonicTimestampCount = nonMonotonicCount,
            irregularGapCount = irregularGapCount,
            missingAccuracyCount = missingAccuracyCount,
            nonFiniteValueCount = nonFiniteValueCount,
            timestampEpochOffsetRangeMillis = minEpochOffset?.let { min -> maxEpochOffset?.let { min..it } },
            timeline = timeline
        )
    }

    fun medianIntervalNanos(): Double? = medianInterval.estimate()
    fun firstTimestampNanos(): Long? = firstNanos
    fun lastTimestampNanos(): Long? = lastNanos

    companion object {
        fun magnitude(x: Float, y: Float, z: Float): Double? {
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
            return sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z).takeIf(Double::isFinite)
        }
    }
}

class SensorTimelineAccumulator(
    private val type: SensorStreamType,
    private val startTimestampNanos: Long,
    private val endTimestampNanos: Long,
    eventCount: Long,
    maxBuckets: Int = DEFAULT_MAX_BUCKETS
) {
    private val bucketCount = minOf(eventCount.coerceAtLeast(1L), maxBuckets.toLong()).toInt().coerceAtLeast(1)
    private val buckets = arrayOfNulls<MutableBucket>(bucketCount)
    private val span = (endTimestampNanos - startTimestampNanos).coerceAtLeast(0L)

    init { require(maxBuckets > 0) }

    fun add(event: RawSensorEvent) {
        if (event.type.name != type.name || !event.x.isFinite() || !event.y.isFinite() || !event.z.isFinite()) return
        val ratio = if (span == 0L) 0.0 else ((event.timestampNanos - startTimestampNanos).toDouble() / span).coerceIn(0.0, 1.0)
        val index = floor(ratio * bucketCount).toInt().coerceIn(0, bucketCount - 1)
        val bucket = buckets[index] ?: MutableBucket(index).also { buckets[index] = it }
        bucket.add(event, centerNanos(index))
    }

    fun finish(): List<SensorTimelineBucket> = buckets.mapNotNull { bucket ->
        bucket?.let {
            val startOffset = if (span == 0L) 0.0 else span.toDouble() * it.index / bucketCount / 1_000_000_000.0
            val endOffset = if (span == 0L) startOffset else span.toDouble() * (it.index + 1) / bucketCount / 1_000_000_000.0
            SensorTimelineBucket(startOffset, endOffset, it.minX, it.maxX, it.minY, it.maxY, it.minZ, it.maxZ, it.selected!!)
        }
    }

    private fun centerNanos(index: Int): Long = if (span == 0L) startTimestampNanos else startTimestampNanos + (span.toDouble() * (index + 0.5) / bucketCount).toLong()

    private class MutableBucket(val index: Int) {
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        var selected: SensorTimelineSample? = null
        private var selectedDistance = Double.POSITIVE_INFINITY

        fun add(event: RawSensorEvent, centerNanos: Long) {
            minX = minOf(minX, event.x); maxX = maxOf(maxX, event.x)
            minY = minOf(minY, event.y); maxY = maxOf(maxY, event.y)
            minZ = minOf(minZ, event.z); maxZ = maxOf(maxZ, event.z)
            val distance = abs(event.timestampNanos.toDouble() - centerNanos)
            if (distance < selectedDistance) {
                selectedDistance = distance
                selected = SensorTimelineSample(event.timestampNanos, event.timestampEpochMillis, event.x, event.y, event.z, event.accuracy)
            }
        }
    }

    companion object { const val DEFAULT_MAX_BUCKETS = 240 }
}
