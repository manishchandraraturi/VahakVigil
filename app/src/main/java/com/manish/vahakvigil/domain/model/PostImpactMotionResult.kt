package com.manish.vahakvigil.domain.model

/**
 * Domain model describing the result of post-impact motion analysis produced by
 * [com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer].
 *
 * ## What this represents
 *
 * Quantifies the physical motion observed in the sensor stream following a [QualifiedIncident].
 * Evaluates whether the device has come to a sustained rest (stillness) or continues to move.
 *
 * ## Important safety distinction
 *
 * This result describes **raw device motion only**. It does not infer or prove whether
 * the rider is unhurt, incapacitated, or involved in an accident.
 *
 * @param status
 *   The current post-impact classification ([PostImpactStatus.OBSERVING],
 *   [PostImpactStatus.MOVING], or [PostImpactStatus.STILL]).
 * @param observationStartMs
 *   Start timestamp of the post-impact observation period.
 * @param observationEndMs
 *   End timestamp of the post-impact observation period.
 * @param stillnessDurationMs
 *   The continuous duration (ms) of unbroken stillness currently observed.
 * @param peakAccelerationMagnitude
 *   Peak acceleration magnitude observed across valid post-impact readings.
 * @param peakGyroscopeMagnitude
 *   Peak gyroscope magnitude observed across valid post-impact readings.
 * @param readingCount
 *   Total number of valid post-impact readings evaluated in this window.
 */
data class PostImpactMotionResult(
    val status: PostImpactStatus,
    val observationStartMs: Long,
    val observationEndMs: Long,
    val stillnessDurationMs: Long,
    val peakAccelerationMagnitude: Float,
    val peakGyroscopeMagnitude: Float,
    val readingCount: Int
)
