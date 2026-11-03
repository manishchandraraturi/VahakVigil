package com.manish.vahakvigil.domain.model

/**
 * Configuration model for [com.manish.vahakvigil.domain.analyzer.EventQualificationEngine].
 *
 * Defines the criteria required to qualify persistent candidate accident events
 * before they can be considered a [QualifiedIncident].
 *
 * ## ⚠️ PLACEHOLDER VALUES
 *
 * All default values in this class — minimum confidence score, required consecutive windows,
 * and qualification time window — are engineering placeholders chosen for testing and
 * early development. None of these values have been empirically validated using real-world
 * motorcycle crash telemetry.
 *
 * Rigorous calibration with labeled dataset telemetry is required before these values
 * can be used in production.
 *
 * @param minimumConfidenceScore
 *   Minimum [ConfidenceScore.value] (0.0 to 1.0) a candidate event must possess to be
 *   considered qualifying. Candidate events below this score break consecutive qualification streaks.
 *   Default: 0.60. PLACEHOLDER.
 *
 * @param requiredConsecutiveWindows
 *   Number of consecutive qualifying candidate windows required to produce a [QualifiedIncident].
 *   Prevents single isolated spikes or transient disturbances from qualifying.
 *   Default: 3. PLACEHOLDER.
 *
 * @param qualificationWindowMillis
 *   Maximum elapsed duration (in milliseconds) across which the consecutive qualifying candidate
 *   windows must occur. If qualifying windows are too far apart, the sequence expires.
 *   Default: 2000L (2 seconds). PLACEHOLDER.
 */
data class EventQualificationConfig(
    val minimumConfidenceScore: Float = 0.60f,
    val requiredConsecutiveWindows: Int = 3,
    val qualificationWindowMillis: Long = 2000L
) {
    init {
        require(minimumConfidenceScore in 0.0f..1.0f) {
            "minimumConfidenceScore must be in [0.0, 1.0], got $minimumConfidenceScore"
        }
        require(requiredConsecutiveWindows >= 1) {
            "requiredConsecutiveWindows must be at least 1, got $requiredConsecutiveWindows"
        }
        require(qualificationWindowMillis > 0L) {
            "qualificationWindowMillis must be positive, got $qualificationWindowMillis"
        }
    }

    companion object {
        val DEFAULT: EventQualificationConfig = EventQualificationConfig()
    }
}
