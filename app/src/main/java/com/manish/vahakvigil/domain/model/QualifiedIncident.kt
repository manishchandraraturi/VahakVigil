package com.manish.vahakvigil.domain.model

/**
 * Domain model representing a qualified sensor incident produced by
 * [com.manish.vahakvigil.domain.analyzer.EventQualificationEngine].
 *
 * ## What this represents
 *
 * A [QualifiedIncident] is produced when candidate accident events satisfy deterministic
 * qualification criteria: meeting the minimum confidence score across multiple consecutive
 * candidate windows within a bounded qualification time window.
 *
 * ## Important distinction: NOT a confirmed crash
 *
 * This model represents a **qualified sensor incident**, NOT proof of a medical or vehicular accident.
 * Severe non-accident disturbances (e.g. phone dropped on asphalt, aggressive off-road riding,
 * sudden emergency maneuvers) can produce high sensor evidence.
 *
 * A [QualifiedIncident] does NOT trigger:
 * - Emergency mode
 * - SMS / phone calls
 * - Contact notifications
 * - Siren / flashlight / vibration alarms
 * - Foreground services
 *
 * It is solely an intermediate domain qualification signal that feeds into a future
 * decision layer.
 *
 * @param windowStartMs
 *   Start timestamp (ms) of the earliest candidate event that contributed to this qualification.
 * @param windowEndMs
 *   End timestamp (ms) of the latest candidate event that completed this qualification.
 * @param qualifyingConfidenceScore
 *   The peak confidence score observed across the qualifying sequence.
 * @param qualifyingWindowCount
 *   The number of consecutive qualifying windows that met the criteria.
 * @param candidateEvents
 *   The list of [CandidateAccidentEvent]s that comprised the qualifying sequence.
 */
data class QualifiedIncident(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val qualifyingConfidenceScore: ConfidenceScore,
    val qualifyingWindowCount: Int,
    val candidateEvents: List<CandidateAccidentEvent>
) {
    /** The candidate accident event that completed the qualification sequence. */
    val triggeringCandidateEvent: CandidateAccidentEvent
        get() = candidateEvents.last()
}
