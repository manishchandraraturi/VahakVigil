package com.manish.vahakvigil.ui.screens.home

import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EmergencySmsMessage
import com.manish.vahakvigil.domain.model.MotionEvent
import com.manish.vahakvigil.domain.model.PostImpactMotionResult
import com.manish.vahakvigil.domain.model.QualifiedIncident
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.RidePlanningState

/**
 * UI State for the Home screen.
 *
 * Exposes real-time monitoring status, sensor readings, the latest per-reading
 * motion event from [com.manish.vahakvigil.domain.analyzer.MotionAnalyzer],
 * the latest temporal pattern result from
 * [com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer],
 * the latest candidate accident event from
 * [com.manish.vahakvigil.domain.analyzer.ConfidenceScorer],
 * the latest qualified incident from
 * [com.manish.vahakvigil.domain.analyzer.EventQualificationEngine],
 * the latest post-impact motion result from
 * [com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer],
 * the emergency decision and cancellation countdown state,
 * the prospective emergency action orchestration result, and
 * the compiled emergency dispatch payload.
 *
 * Note: None of these values indicate a confirmed crash. They represent raw
 * sensor evidence, candidate signals, qualified incidents, post-impact motion states,
 * cancellable countdown states, and architectural dispatch triggers.
 */
data class HomeUiState(
    val isMonitoring: Boolean = false,
    val latestReading: SensorReading? = null,
    val capabilities: SensorCapabilities = SensorCapabilities(),
    val latestMotionEvent: MotionEvent? = null,
    val displayedMotionEvent: MotionEvent? = null,
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
    val userAcknowledgedNeedHelp: Boolean = false,
    val emergencyDispatchUiState: EmergencyDispatchUiState = EmergencyDispatchUiState.IDLE,
    val emergencyIncidentId: String? = null,
    val currentDateFormatted: String = "",
    val currentTimeFormatted: String = "",
    val timeRegionFormatted: String = "",
    val timezoneOffsetFormatted: String = "",
    val greetingFormatted: String = "Good morning",
    val permissionExplanation: String? = null,
    val permissionLimitations: List<String> = emptyList(),
    val isSmsPermissionGranted: Boolean = true,
    val isLocationPermissionGranted: Boolean = true,
    val isNotificationPermissionGranted: Boolean = true,
    val rideLocationState: com.manish.vahakvigil.domain.model.RideLocationState = com.manish.vahakvigil.domain.model.RideLocationState.Inactive,
    val ridePath: List<com.manish.vahakvigil.domain.model.RideLocationPoint> = emptyList(),
    val rideStartedAtEpochMillis: Long? = null,
    val rideDistanceMeters: Double? = null,
    val ridePlanning: RidePlanningState = RidePlanningState()
)



