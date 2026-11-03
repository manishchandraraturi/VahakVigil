package com.manish.vahakvigil.domain.model

/**
 * An ordered, immutable snapshot of recent [SensorReading] values used by
 * [com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer].
 *
 * ## Purpose
 *
 * Individual sensor readings analyzed by [MotionAnalyzer] can identify whether
 * a single reading is abnormal, but cannot determine whether abnormal readings
 * form a meaningful temporal sequence. [MotionWindow] carries the recent slice
 * of readings needed for that second-level analysis.
 *
 * ## Ordering
 *
 * Readings are stored in chronological order: [readings].first() is the oldest,
 * [readings].last() is the most recent. The [TemporalMotionAnalyzer] relies on
 * this ordering for its sequential pattern scan.
 *
 * ## Android independence
 *
 * This class has no dependency on [android.hardware.SensorEvent],
 * [android.hardware.SensorManager], [android.content.Context], Compose, or
 * ViewModel. It is fully unit-testable on a plain JVM.
 *
 * @param readings Chronologically ordered list of recent sensor snapshots.
 *                 May be empty when monitoring has just started.
 */
data class MotionWindow(
    val readings: List<SensorReading>
) {
    /** True when there are no readings in this window. */
    val isEmpty: Boolean get() = readings.isEmpty()

    /** The timestamp of the oldest reading, or null if empty. */
    val startTimestamp: Long? get() = readings.firstOrNull()?.timestamp

    /** The timestamp of the most recent reading, or null if empty. */
    val endTimestamp: Long? get() = readings.lastOrNull()?.timestamp
}
