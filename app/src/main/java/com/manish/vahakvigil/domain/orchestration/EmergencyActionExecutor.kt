package com.manish.vahakvigil.domain.orchestration

import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus

/**
 * Pure Kotlin contract for executing prospective emergency actions.
 *
 * Platform implementations (SMS Manager, Telephony, Location Services)
 * will implement this interface in future milestones.
 */
interface EmergencyActionExecutor {
    fun execute(request: EmergencyActionRequest): EmergencyActionResult
}

/**
 * Safe default no-op executor for architectural simulation.
 * Does NOT access any device hardware, telephony, or external services.
 */
class NoOpEmergencyActionExecutor : EmergencyActionExecutor {
    override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
        return EmergencyActionResult(
            status = EmergencyActionResultStatus.TRIGGERED,
            actionType = request.actionType,
            timestamp = request.incidentTimestamp,
            details = "Simulated architectural trigger (No-op executor)"
        )
    }
}
