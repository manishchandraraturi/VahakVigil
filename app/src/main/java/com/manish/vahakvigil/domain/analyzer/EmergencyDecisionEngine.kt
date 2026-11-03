package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.PostImpactMotionResult
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.QualifiedIncident

/**
 * Pure Kotlin decision engine that combines [QualifiedIncident] and [PostImpactMotionResult]
 * to determine whether the criteria to enter the cancellable emergency stage are met.
 *
 * ## Decision Rules
 * - [EmergencyDecisionState.NO_EMERGENCY] if:
 *   - [QualifiedIncident] is absent (when required).
 *   - [PostImpactMotionResult] is absent (when required).
 *   - Post-impact status is not [PostImpactStatus.STILL] (when required).
 * - [EmergencyDecisionState.EMERGENCY_CANDIDATE] when:
 *   - All configured prerequisites are met (qualified incident exists and post-impact status is STILL).
 *
 * ## Android independence
 * Pure JVM Kotlin. Completely decoupled from Context, Activity, SensorManager, Compose, and background services.
 *
 * @param config Configuration rules. Defaults to [EmergencyDecisionConfig.DEFAULT].
 */
class EmergencyDecisionEngine(
    val config: EmergencyDecisionConfig = EmergencyDecisionConfig.DEFAULT
) {

    /**
     * Evaluates the current incident and post-impact evidence against the decision rules.
     *
     * @param incident The qualified sensor incident, or null if none has qualified.
     * @param postImpactResult The observed post-impact motion state, or null if not yet observed.
     * @return [EmergencyDecision] representing the determination and its underlying rationale.
     */
    fun evaluate(
        incident: QualifiedIncident?,
        postImpactResult: PostImpactMotionResult?
    ): EmergencyDecision {
        if (config.requireQualifiedIncident && incident == null) {
            return EmergencyDecision(
                state = EmergencyDecisionState.NO_EMERGENCY,
                reason = "No qualified incident",
                incidentTimestampMs = null,
                postImpactStatus = postImpactResult?.status
            )
        }

        if (config.requireStillPostImpact) {
            if (postImpactResult == null) {
                return EmergencyDecision(
                    state = EmergencyDecisionState.NO_EMERGENCY,
                    reason = "No post-impact motion result",
                    incidentTimestampMs = incident?.windowEndMs,
                    postImpactStatus = null
                )
            }

            if (postImpactResult.status != PostImpactStatus.STILL) {
                return EmergencyDecision(
                    state = EmergencyDecisionState.NO_EMERGENCY,
                    reason = "Post-impact motion state is ${postImpactResult.status}, not STILL",
                    incidentTimestampMs = incident?.windowEndMs,
                    postImpactStatus = postImpactResult.status
                )
            }
        }

        return EmergencyDecision(
            state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
            reason = "Qualified incident confirmed with sustained post-impact stillness",
            incidentTimestampMs = incident?.windowEndMs,
            postImpactStatus = postImpactResult?.status
        )
    }
}
