package com.manish.vahakvigil.domain.model

/**
 * Domain model representing the result of a temporal pattern analysis
 * performed by [com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer].
 *
 * Unlike [MotionEvent] — which describes a single-reading threshold breach —
 * [TemporalMotionEvent] describes a pattern spanning multiple readings over time.
 *
 * This is NOT a confirmed crash event. It is a raw multi-sensor temporal signal.
 * A future confidence-scoring layer is required before any emergency action is taken.
 *
 * @param eventType      Classification of the detected temporal pattern.
 * @param windowStartMs  Timestamp of the first reading considered in this analysis window (ms).
 * @param windowEndMs    Timestamp of the last reading considered in this analysis window (ms).
 * @param peakAccelerationMagnitude
 *   The highest acceleration magnitude observed across all readings in the window (m/s²).
 *   Retained for future confidence scoring.
 * @param peakGyroscopeMagnitude
 *   The highest gyroscope magnitude observed across all readings in the window (rad/s).
 *   Retained for future confidence scoring.
 * @param readingCount   Number of readings that were present in the analysis window.
 */
data class TemporalMotionEvent(
    val eventType: TemporalMotionEventType,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val peakAccelerationMagnitude: Float,
    val peakGyroscopeMagnitude: Float,
    val readingCount: Int
)
