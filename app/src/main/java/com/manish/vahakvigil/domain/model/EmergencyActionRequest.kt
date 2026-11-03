package com.manish.vahakvigil.domain.model

/**
 * Domain request carrying information required by the future action dispatch layer.
 * Contains no Android platform objects (no Context, Intent, Location, etc.).
 *
 * @param incidentTimestamp Timestamp of the qualified incident.
 * @param actionType The prospective action to perform.
 * @param reason Reason and context for the dispatch.
 */
data class EmergencyActionRequest(
    val incidentTimestamp: Long,
    val actionType: EmergencyActionType,
    val reason: String,
    val dispatchPayload: EmergencyDispatchPayload? = null
)
