package com.manish.vahakvigil.data.sensor

/** Converts Android monotonic sensor timestamps using a sampled wall-clock/monotonic offset. */
internal class SensorTimestampConverter(private val epochOffsetMillis: Long) {
    fun toEpochMillis(timestampNanos: Long): Long = timestampNanos / NANOS_PER_MILLI + epochOffsetMillis

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        fun fromClockSample(epochMillis: Long, elapsedRealtimeNanos: Long): SensorTimestampConverter =
            SensorTimestampConverter(epochMillis - elapsedRealtimeNanos / NANOS_PER_MILLI)
    }
}
