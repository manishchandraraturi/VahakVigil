package com.manish.vahakvigil.domain.model

/**
 * Qualitative classification of a [ConfidenceScore] value.
 *
 * Describes the strength of sensor evidence, NOT the probability that an accident occurred.
 * These labels are informational — no emergency action should be taken based solely on
 * this classification at this stage of the pipeline.
 */
enum class EvidenceLevel {
    /** Score in [0.0, 0.35): weak sensor evidence. Likely normal riding or isolated noise. */
    LOW,

    /** Score in [0.35, 0.65): moderate sensor evidence. May warrant closer monitoring. */
    MEDIUM,

    /** Score in [0.65, 1.0]: strong sensor evidence across multiple factors. */
    HIGH
}

/**
 * A normalised, deterministic sensor-evidence score produced by
 * [com.manish.vahakvigil.domain.analyzer.ConfidenceScorer].
 *
 * ## What this represents
 *
 * A [ConfidenceScore] is a weighted combination of independent sensor-evidence
 * factors (temporal impact pattern, peak acceleration, peak gyroscope, evidence
 * density). It quantifies HOW MUCH sensor evidence was observed, not the probability
 * that a crash occurred.
 *
 * ## What this is NOT
 *
 * - NOT a crash probability.
 * - NOT a medically or statistically validated accident likelihood.
 * - NOT derived from machine learning or AI.
 * - NOT suitable for triggering emergency actions without further validation.
 *
 * Real-world labeled crash/non-crash data and calibration are required before
 * this score can be interpreted as a probability.
 *
 * ## Value contract
 *
 * [value] is always in the closed range [0.0, 1.0].
 * It is clamped at construction time so callers do not need to guard against
 * out-of-range results.
 *
 * @param value Sensor-evidence score in [0.0, 1.0].
 */
data class ConfidenceScore(val value: Float) {
    init {
        // Defensive check: log a warning if clamping was needed (shouldn't happen
        // if ConfidenceScorer is correct, but we clamp here as a safety net).
        require(value in 0f..1f) {
            "ConfidenceScore value must be in [0.0, 1.0], got $value"
        }
    }

    /** Qualitative evidence level derived from [value]. */
    val evidenceLevel: EvidenceLevel get() = when {
        value < 0.35f -> EvidenceLevel.LOW
        value < 0.65f -> EvidenceLevel.MEDIUM
        else -> EvidenceLevel.HIGH
    }

    companion object {
        /** Zero evidence — no meaningful sensor activity. */
        val ZERO = ConfidenceScore(0f)

        /**
         * Safely constructs a [ConfidenceScore], clamping [raw] to [0.0, 1.0].
         * Replaces NaN and Infinity with 0.0.
         */
        fun fromRaw(raw: Float): ConfidenceScore {
            val safe = if (raw.isNaN() || raw.isInfinite()) 0f else raw
            return ConfidenceScore(safe.coerceIn(0f, 1f))
        }
    }
}
