package com.manish.vahakvigil.domain.orchestration

import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload

/**
 * Pure Kotlin orchestrator for prospective emergency actions.
 *
 * Responsibilities:
 * - Accept an [EmergencyDecision] and [EmergencyCountdownState]
 * - Trigger emergency actions ONLY when:
 *     decision.state == EMERGENCY_CANDIDATE AND countdownState == EXPIRED
 * - Never trigger actions when countdown is CANCELLED, IDLE, or COUNTING_DOWN
 * - Never trigger actions when decision is NO_EMERGENCY or null
 * - Prevent duplicate action execution for the same incident
 * - Ensure that a cancelled incident never triggers an action even upon later updates
 * - Maintain pure Kotlin abstraction with zero direct dependencies on Android framework,
 *   telephony, SMS, GPS, or network services
 */
class EmergencyActionOrchestrator(
    private val actionExecutor: EmergencyActionExecutor = NoOpEmergencyActionExecutor()
) {
    private var dispatchedIncidentKey: Long? = null
    private var cancelledIncidentKey: Long? = null

    var latestResult: EmergencyActionResult = EmergencyActionResult.notTriggered()
        private set

    /**
     * Evaluates the current [decision] and [countdownState] to determine whether
     * an emergency action should be dispatched.
     *
     * @param decision The latest evaluated emergency decision.
     * @param countdownState The current state of the cancellation countdown.
     * @param actionType The prospective action to dispatch. Defaults to [EmergencyActionType.SMS].
     * @return The [EmergencyActionResult] of the evaluation.
     */
    fun evaluate(
        decision: EmergencyDecision?,
        countdownState: EmergencyCountdownState,
        actionType: EmergencyActionType = EmergencyActionType.SMS,
        dispatchPayload: EmergencyDispatchPayload? = null
    ): EmergencyActionResult {
        val timestamp = decision?.incidentTimestampMs ?: 0L

        // Rule 1: Must have an active EMERGENCY_CANDIDATE decision
        if (decision == null || decision.state != EmergencyDecisionState.EMERGENCY_CANDIDATE) {
            latestResult = EmergencyActionResult.notTriggered(
                actionType = actionType,
                timestamp = timestamp,
                reason = "No emergency candidate active"
            )
            return latestResult
        }

        val incidentKey = decision.incidentTimestampMs ?: 0L

        // Rule 2: If cancelled, record cancellation and reject
        if (countdownState == EmergencyCountdownState.CANCELLED) {
            cancelledIncidentKey = incidentKey
            latestResult = EmergencyActionResult.notTriggered(
                actionType = actionType,
                timestamp = timestamp,
                reason = "Countdown cancelled by user"
            )
            return latestResult
        }

        // Rule 3: If this incident was previously cancelled, never execute
        if (cancelledIncidentKey == incidentKey) {
            latestResult = EmergencyActionResult.notTriggered(
                actionType = actionType,
                timestamp = timestamp,
                reason = "Incident was previously cancelled"
            )
            return latestResult
        }

        // Rule 4: Countdown must be EXPIRED
        if (countdownState != EmergencyCountdownState.EXPIRED) {
            latestResult = EmergencyActionResult.notTriggered(
                actionType = actionType,
                timestamp = timestamp,
                reason = "Countdown in state $countdownState (not EXPIRED)"
            )
            return latestResult
        }

        // Rule 5: Duplicate prevention - do not dispatch twice for the same incident
        if (dispatchedIncidentKey == incidentKey) {
            return latestResult
        }

        // Trigger action via injected executor
        val request = EmergencyActionRequest(
            incidentTimestamp = incidentKey,
            actionType = actionType,
            reason = decision.reason.ifEmpty { "Emergency countdown expired" },
            dispatchPayload = dispatchPayload
        )
        val result = actionExecutor.execute(request)
        if (result.status == EmergencyActionResultStatus.TRIGGERED) {
            dispatchedIncidentKey = incidentKey
        }
        latestResult = result
        return result
    }

    /**
     * Resets the orchestrator state, clearing dispatched and cancelled tracking.
     */
    fun reset() {
        dispatchedIncidentKey = null
        cancelledIncidentKey = null
        latestResult = EmergencyActionResult.notTriggered()
    }
}
