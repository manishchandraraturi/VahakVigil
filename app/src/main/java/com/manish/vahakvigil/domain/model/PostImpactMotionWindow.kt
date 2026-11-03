package com.manish.vahakvigil.domain.model

/**
 * An ordered, immutable collection of [SensorReading]s collected after a [QualifiedIncident]
 * for evaluation by [com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer].
 *
 * ## Constraints
 * - Pure Kotlin with no Android framework dependencies.
 * - Bounded in memory to the configured observation window duration.
 *
 * @param readings Chronological list of sensor readings recorded in the post-impact period.
 */
data class PostImpactMotionWindow(
    val readings: List<SensorReading>
) {
    /** True if no post-impact readings have been collected. */
    val isEmpty: Boolean get() = readings.isEmpty()

    /** Total number of readings in this window. */
    val size: Int get() = readings.size

    /** Start timestamp of the oldest reading in the window, or null if empty. */
    val startTimestamp: Long? get() = readings.firstOrNull()?.timestamp

    /** End timestamp of the newest reading in the window, or null if empty. */
    val endTimestamp: Long? get() = readings.lastOrNull()?.timestamp
}
