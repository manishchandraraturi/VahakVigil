package com.manish.vahakvigil.domain.model

/**
 * Pure Kotlin domain model representing the complete compiled payload
 * for prospective emergency notification dispatch.
 *
 * @param incidentTimestamp Timestamp of the qualified incident.
 * @param medicalSummary Medical emergency summary from user's persisted profile, or null if unconfigured.
 * @param emergencyContacts Ordered list of persisted emergency contacts to receive dispatch.
 * @param location Geographic location placeholder ([EmergencyLocation.Unavailable] until GPS milestone).
 * @param localEvidence Optional device-local evidence metadata. Dispatch/SMS formatters must not serialize it.
 */
data class EmergencyDispatchPayload(
    val incidentTimestamp: Long,
    val medicalSummary: EmergencyMedicalSummary?,
    val emergencyContacts: List<EmergencyDispatchContact>,
    val location: EmergencyLocation = EmergencyLocation.Unavailable,
    val hospital: EmergencyHospital? = null,
    val localEvidence: LocalIncidentEvidence? = null
)
