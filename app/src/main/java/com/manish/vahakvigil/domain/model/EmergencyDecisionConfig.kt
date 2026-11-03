package com.manish.vahakvigil.domain.model

/**
 * Configuration model for [com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine].
 *
 * Defines the criteria required to transition from post-impact motion observation
 * into a cancellable emergency candidate state with a user countdown.
 *
 * ## ⚠️ PLACEHOLDER RULES
 *
 * These rules represent an early deterministic engineering foundation and have NOT
 * been validated in clinical or real-world crash scenarios.
 *
 * @param requireQualifiedIncident
 *   Whether a [QualifiedIncident] is strictly required to declare an emergency candidate.
 *   Default: true.
 *
 * @param requireStillPostImpact
 *   Whether post-impact state must be [PostImpactStatus.STILL] to declare an emergency candidate.
 *   Default: true.
 *
 * @param countdownDurationMillis
 *   Total duration (in milliseconds) of the cancellation countdown before it expires.
 *   Default: 75_000L (75 seconds, as specified in product requirements).
 */
data class EmergencyDecisionConfig(
    val requireQualifiedIncident: Boolean = true,
    val requireStillPostImpact: Boolean = true,
    val countdownDurationMillis: Long = 75_000L
) {
    init {
        require(countdownDurationMillis > 0L) {
            "countdownDurationMillis must be positive, got $countdownDurationMillis"
        }
    }

    companion object {
        val DEFAULT: EmergencyDecisionConfig = EmergencyDecisionConfig()
    }
}
