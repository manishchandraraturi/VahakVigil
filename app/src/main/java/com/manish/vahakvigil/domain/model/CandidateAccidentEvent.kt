package com.manish.vahakvigil.domain.model

/**
 * A candidate accident event produced by the confidence-scoring pipeline.
 *
 * ## What this represents
 *
 * A [CandidateAccidentEvent] is generated when the sensor-evidence score produced by
 * [com.manish.vahakvigil.domain.analyzer.ConfidenceScorer] is non-trivial. It packages
 * the score together with the temporal evidence that drove it and provides enough context
 * for the future decision layer to act (or not act) upon.
 *
 * ## Naming
 *
 * This is deliberately called a CANDIDATE event, not a confirmed crash or accident.
 * Many real-world scenarios — speed bumps, potholes, emergency braking — can produce
 * sensor patterns that generate a candidate event. A future confidence threshold,
 * persistence check, and potentially GPS / contextual data are required before any
 * emergency action is appropriate.
 *
 * ## No emergency actions here
 *
 * A [CandidateAccidentEvent] does NOT trigger SMS, calls, flashlight, alarm, or
 * emergency contacts. It is informational only at this stage.
 *
 * @param windowStartMs     Timestamp (ms) of the first reading in the analysis window.
 * @param windowEndMs       Timestamp (ms) of the last reading in the analysis window.
 * @param confidenceScore   Sensor-evidence score in [0.0, 1.0].
 * @param temporalEvent     The [TemporalMotionEvent] that generated this candidate.
 * @param readingCount      Number of sensor readings that contributed to this candidate.
 */
data class CandidateAccidentEvent(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val confidenceScore: ConfidenceScore,
    val temporalEvent: TemporalMotionEvent,
    val readingCount: Int
)
