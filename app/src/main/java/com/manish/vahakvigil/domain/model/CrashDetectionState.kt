package com.manish.vahakvigil.domain.model

/**
 * Immutable state produced by [com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline].
 *
 * Represents the real-time detection, analysis, and emergency outcome states
 * independent of any UI or Activity lifecycle.
 */
data class CrashDetectionState(
    val latestReading: SensorReading? = null,
    val latestMotionEvent: MotionEvent? = null,
    val latestTemporalEvent: TemporalMotionEvent? = null,
    val latestCandidateAccidentEvent: CandidateAccidentEvent? = null,
    val latestQualifiedIncident: QualifiedIncident? = null,
    val latestPostImpactResult: PostImpactMotionResult? = null,
    val latestEmergencyDecision: EmergencyDecision? = null,
    val emergencyCountdownState: EmergencyCountdownState = EmergencyCountdownState.IDLE,
    val emergencyCountdownRemainingMillis: Long = 75_000L,
    val latestEmergencyActionResult: EmergencyActionResult? = null,
    val latestEmergencyDispatchPayload: EmergencyDispatchPayload? = null,
    val latestEmergencySmsMessage: EmergencySmsMessage? = null,
    val isEmergencyModeActive: Boolean = false,
    val emergencyDispatchUiState: EmergencyDispatchUiState = EmergencyDispatchUiState.IDLE,
    val userAcknowledgedNeedHelp: Boolean = false,
    val emergencyIncidentId: String? = null
)
