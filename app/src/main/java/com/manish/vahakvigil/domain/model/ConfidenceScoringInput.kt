package com.manish.vahakvigil.domain.model

/**
 * Input bundle for [com.manish.vahakvigil.domain.analyzer.ConfidenceScorer].
 *
 * Aggregates the evidence already extracted by the earlier pipeline stages into a
 * single value object so the scorer is self-contained and independently testable.
 *
 * Reuses existing domain models — no data is duplicated unnecessarily.
 *
 * @param temporalEvent
 *   Result of the temporal pattern analysis over the current [MotionWindow].
 *   Always non-null; the scorer reads [TemporalMotionEvent.eventType] to determine
 *   whether a multi-sensor sequence was detected.
 *
 * @param peakAccelerationMagnitude
 *   Highest acceleration magnitude (m/s²) observed across all readings in the window.
 *   Sourced from [TemporalMotionEvent.peakAccelerationMagnitude].
 *
 * @param peakGyroscopeMagnitude
 *   Highest gyroscope magnitude (rad/s) observed across all readings in the window.
 *   Sourced from [TemporalMotionEvent.peakGyroscopeMagnitude].
 *
 * @param accelerationBreachCount
 *   Number of readings in the window whose acceleration magnitude exceeded the
 *   configured threshold. Used for the evidence-density (persistence) factor.
 *
 * @param gyroscopeBreachCount
 *   Number of readings in the window whose gyroscope magnitude exceeded the
 *   configured threshold. Used for the evidence-density (persistence) factor.
 *
 * @param windowReadingCount
 *   Total number of readings present in the analysis window.
 *   Sourced from [TemporalMotionEvent.readingCount].
 */
data class ConfidenceScoringInput(
    val temporalEvent: TemporalMotionEvent,
    val peakAccelerationMagnitude: Float,
    val peakGyroscopeMagnitude: Float,
    val accelerationBreachCount: Int,
    val gyroscopeBreachCount: Int,
    val windowReadingCount: Int
) {
    /** Total number of individual threshold breaches across both sensors. */
    val totalBreachCount: Int get() = accelerationBreachCount + gyroscopeBreachCount
}
