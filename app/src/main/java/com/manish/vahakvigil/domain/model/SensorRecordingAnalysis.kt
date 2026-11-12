package com.manish.vahakvigil.domain.model

enum class SensorStreamType { ACCELEROMETER, GYROSCOPE }

data class SensorTimelineSample(
    val timestampNanos: Long,
    val timestampEpochMillis: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int
)

/** One bounded time bucket. Axis ranges preserve recorded extrema; selectedSample is an actual event. */
data class SensorTimelineBucket(
    val startOffsetSeconds: Double,
    val endOffsetSeconds: Double,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val selectedSample: SensorTimelineSample
)

data class SensorStreamAnalysis(
    val type: SensorStreamType,
    val eventCount: Long,
    val firstTimestampNanos: Long,
    val lastTimestampNanos: Long,
    val minimumTimestampNanos: Long,
    val maximumTimestampNanos: Long,
    val firstTimestampEpochMillis: Long,
    val lastTimestampEpochMillis: Long,
    val averageMagnitude: Double?,
    val peakMagnitude: Double?,
    val minimumSampleIntervalMillis: Double?,
    val medianSampleIntervalMillisApprox: Double?,
    val maximumSampleIntervalMillis: Double?,
    val sampleFrequencyHzApprox: Double?,
    val duplicateTimestampCount: Long,
    val nonMonotonicTimestampCount: Long,
    val irregularGapCount: Long,
    val missingAccuracyCount: Long,
    val nonFiniteValueCount: Long,
    val timestampEpochOffsetRangeMillis: LongRange?,
    val timeline: List<SensorTimelineBucket>
)

data class SensorRecordingAnalysis(
    val totalEventCount: Long,
    val accelerometer: SensorStreamAnalysis?,
    val gyroscope: SensorStreamAnalysis?
)

enum class RideRecordingCompleteness { COMPLETE, PARTIAL, NO_SENSOR_DATA }

/** Conservative coverage description; it is not a safety or ride-quality verdict. */
object RideRecordingCompletenessPolicy {
    fun evaluate(
        sessionStatus: RideSessionStatus,
        gpsPointCount: Int,
        accelerometerCount: Long,
        gyroscopeCount: Long
    ): RideRecordingCompleteness = when {
        accelerometerCount == 0L && gyroscopeCount == 0L -> RideRecordingCompleteness.NO_SENSOR_DATA
        sessionStatus != RideSessionStatus.COMPLETED || gpsPointCount < 2 || accelerometerCount == 0L || gyroscopeCount == 0L -> RideRecordingCompleteness.PARTIAL
        else -> RideRecordingCompleteness.COMPLETE
    }
}

enum class SensorGpsTimelineAlignment { SEPARATE_TIMELINES, SENSOR_OR_GPS_DATA_MISSING }

/**
 * Sensor epoch timestamps use a fixed wall-clock/monotonic offset sampled when the Android
 * sensor source is created. GPS timestamps are wall-clock values. Clock changes cannot be
 * excluded from the available recording metadata, so the streams must not be aligned together.
 */
object SensorGpsTimelinePolicy {
    fun alignment(sensorEventCount: Long, gpsPointCount: Int): SensorGpsTimelineAlignment =
        if (sensorEventCount > 0L && gpsPointCount > 0) SensorGpsTimelineAlignment.SEPARATE_TIMELINES
        else SensorGpsTimelineAlignment.SENSOR_OR_GPS_DATA_MISSING
}
