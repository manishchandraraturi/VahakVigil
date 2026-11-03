package com.manish.vahakvigil.domain.model

/**
 * State classification of an emergency decision.
 */
enum class EmergencyDecisionState {
    /** Insufficient or non-qualifying evidence; no emergency action or countdown required. */
    NO_EMERGENCY,

    /**
     * Sensor evidence meets the configured criteria (e.g. qualified incident followed by sustained stillness)
     * to enter the cancellable countdown stage.
     *
     * IMPORTANT: This does NOT mean a crash is confirmed or that the rider is injured.
     * It only signals that the system should solicit user confirmation/cancellation before any future dispatch.
     */
    EMERGENCY_CANDIDATE
}

/**
 * Domain model representing the outcome of an evaluation by
 * [com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine].
 *
 * @param state The decision state ([EmergencyDecisionState.NO_EMERGENCY] or [EmergencyDecisionState.EMERGENCY_CANDIDATE]).
 * @param reason Human-readable deterministic explanation of why the decision was reached.
 * @param incidentTimestampMs Timestamp of the qualifying incident, or null if no incident qualified.
 * @param postImpactStatus Post-impact motion state evaluated, or null if unobserved.
 */
data class EmergencyDecision(
    val state: EmergencyDecisionState,
    val reason: String,
    val incidentTimestampMs: Long?,
    val postImpactStatus: PostImpactStatus?
)
