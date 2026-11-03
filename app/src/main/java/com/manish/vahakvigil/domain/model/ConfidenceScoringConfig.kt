package com.manish.vahakvigil.domain.model

/**
 * Configuration for [com.manish.vahakvigil.domain.analyzer.ConfidenceScorer].
 *
 * Defines the weights applied to each sensor-evidence factor and the reference
 * values used to normalise raw sensor magnitudes into [0.0, 1.0] sub-scores.
 *
 * ## Weight contract
 *
 * All four factor weights MUST sum to exactly 1.0 (within floating-point tolerance).
 * Validated in [init]; throws [IllegalArgumentException] for invalid configurations.
 *
 * ## ⚠️ PLACEHOLDER VALUES
 *
 * Every default value in this class — weights, reference accelerations, and reference
 * gyroscope rates — is a rough engineering placeholder chosen to make the scorer
 * behave reasonably during development. None of these values have been validated
 * against real motorcycle accident data.
 *
 * Real-world labeled crash/non-crash recordings are required before any of these
 * defaults can be trusted in production. Do NOT present the resulting score to users
 * as a calibrated probability.
 *
 * @param patternWeight
 *   Weight given to the temporal impact-pattern factor.
 *   A [TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE] contributes this fraction
 *   to the final score. Default: 0.40. PLACEHOLDER.
 *
 * @param accelerationWeight
 *   Weight given to the normalised peak-acceleration factor.
 *   Default: 0.30. PLACEHOLDER.
 *
 * @param gyroscopeWeight
 *   Weight given to the normalised peak-gyroscope factor.
 *   Default: 0.20. PLACEHOLDER.
 *
 * @param persistenceWeight
 *   Weight given to the evidence-density factor (breach counts / reading density).
 *   Default: 0.10. PLACEHOLDER.
 *
 * @param referenceAccelerationMs2
 *   Acceleration magnitude (m/s²) that maps to a sub-score of approximately 1.0.
 *   Values above this are clamped to 1.0. Default: 50 m/s² — a very rough upper bound
 *   for severe impacts. PLACEHOLDER — not derived from crash physics.
 *
 * @param referenceGyroscopeRads
 *   Gyroscope magnitude (rad/s) that maps to a sub-score of approximately 1.0.
 *   Values above this are clamped to 1.0. Default: 10 rad/s. PLACEHOLDER.
 *
 * @param maxBreachesForFullPersistence
 *   Number of combined threshold breaches required to achieve a full persistence
 *   sub-score of 1.0. Default: 5. PLACEHOLDER.
 */
data class ConfidenceScoringConfig(
    val patternWeight: Float = 0.40f,
    val accelerationWeight: Float = 0.30f,
    val gyroscopeWeight: Float = 0.20f,
    val persistenceWeight: Float = 0.10f,
    val referenceAccelerationMs2: Float = 50f,
    val referenceGyroscopeRads: Float = 10f,
    val maxBreachesForFullPersistence: Int = 5
) {
    init {
        val sum = patternWeight + accelerationWeight + gyroscopeWeight + persistenceWeight
        require(kotlin.math.abs(sum - 1.0f) <= WEIGHT_SUM_TOLERANCE) {
            "ConfidenceScoringConfig weights must sum to 1.0, but got $sum " +
                    "(pattern=$patternWeight, acceleration=$accelerationWeight, " +
                    "gyroscope=$gyroscopeWeight, persistence=$persistenceWeight)"
        }
        require(referenceAccelerationMs2 > 0f) {
            "referenceAccelerationMs2 must be positive, got $referenceAccelerationMs2"
        }
        require(referenceGyroscopeRads > 0f) {
            "referenceGyroscopeRads must be positive, got $referenceGyroscopeRads"
        }
        require(maxBreachesForFullPersistence >= 1) {
            "maxBreachesForFullPersistence must be >= 1, got $maxBreachesForFullPersistence"
        }
    }

    companion object {
        private const val WEIGHT_SUM_TOLERANCE = 0.001f

        /**
         * Creates and validates a [ConfidenceScoringConfig].
         */
        fun create(
            patternWeight: Float = 0.40f,
            accelerationWeight: Float = 0.30f,
            gyroscopeWeight: Float = 0.20f,
            persistenceWeight: Float = 0.10f,
            referenceAccelerationMs2: Float = 50f,
            referenceGyroscopeRads: Float = 10f,
            maxBreachesForFullPersistence: Int = 5
        ): ConfidenceScoringConfig = ConfidenceScoringConfig(
            patternWeight = patternWeight,
            accelerationWeight = accelerationWeight,
            gyroscopeWeight = gyroscopeWeight,
            persistenceWeight = persistenceWeight,
            referenceAccelerationMs2 = referenceAccelerationMs2,
            referenceGyroscopeRads = referenceGyroscopeRads,
            maxBreachesForFullPersistence = maxBreachesForFullPersistence
        )

        /** Default placeholder configuration used when no custom config is provided. */
        val DEFAULT: ConfidenceScoringConfig = ConfidenceScoringConfig()
    }
}
