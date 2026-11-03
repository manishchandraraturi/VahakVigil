package com.manish.vahakvigil.domain.telephony

import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyMedicalSummary

/**
 * Status of external AI voice telephony operations.
 */
enum class VoiceCallStatus {
    /** Telephony/TTS backend provider is not configured. */
    NOT_CONFIGURED,

    /** Call initiation requested with provider. */
    INITIATING,

    /** Active call in progress. */
    IN_PROGRESS,

    /** Call completed successfully. */
    COMPLETED,

    /** Call attempt failed. */
    FAILED
}

/**
 * Result of an AI voice communication request.
 */
data class VoiceCallResult(
    val status: VoiceCallStatus,
    val details: String = ""
)

/**
 * Verified incident context passed to future AI telephony / TTS engine.
 *
 * SAFETY MANDATE: Must ONLY contain verified facts.
 * NEVER infer or invent injuries, bleeding, severity, or ambulance status.
 */
data class EmergencyVoiceCallContext(
    val riderName: String?,
    val incidentTimestamp: Long,
    val location: EmergencyLocation,
    val hospitalName: String?,
    val hospitalPhone: String?,
    val medicalSummary: EmergencyMedicalSummary?
) {
    /**
     * Generates a deterministic, factual text-to-speech message based strictly on verified data.
     */
    fun generateSpokenMessage(): String {
        val riderPart = if (!riderName.isNullOrBlank()) "for rider ${riderName.trim()}" else ""
        val locPart = when (location) {
            is EmergencyLocation.Available ->
                "Current coordinates are ${location.latitude}, ${location.longitude}. Map link: ${location.mapsUrl}"
            is EmergencyLocation.Unavailable ->
                "Current coordinates are unavailable."
        }
        val hospPart = if (!hospitalName.isNullOrBlank()) {
            "Nearby hospital candidate identified: ${hospitalName.trim()}."
        } else {
            ""
        }
        val medPart = if (medicalSummary != null && medicalSummary.bloodGroup.isNotBlank()) {
            "Rider blood group: ${medicalSummary.bloodGroup.trim()}."
        } else {
            ""
        }

        return listOfNotNull(
            "Emergency alert.",
            "A possible road accident has been detected $riderPart.",
            "The rider has not responded to the emergency countdown.",
            "The current rider location is: $locPart",
            hospPart.ifBlank { null },
            medPart.ifBlank { null },
            "Emergency assistance is requested."
        ).joinToString(" ")
    }
}

/**
 * Abstraction for outbound AI voice telephony.
 *
 * Real architecture:
 * VahakVigil -> Secure Backend -> Telephony Provider -> Hospital Phone (AI TTS)
 */
interface EmergencyVoiceCommunicationProvider {
    suspend fun initiateEmergencyCall(phone: String, context: EmergencyVoiceCallContext): VoiceCallResult
    fun getCallStatus(): VoiceCallStatus
}

/**
 * Safe unconfigured implementation of [EmergencyVoiceCommunicationProvider].
 *
 * Avoids faking AI voice calls when backend telephony credentials are not present.
 */
class UnconfiguredVoiceCommunicationProvider : EmergencyVoiceCommunicationProvider {
    override suspend fun initiateEmergencyCall(
        phone: String,
        context: EmergencyVoiceCallContext
    ): VoiceCallResult {
        return VoiceCallResult(
            status = VoiceCallStatus.NOT_CONFIGURED,
            details = "AI voice telephony provider not configured."
        )
    }

    override fun getCallStatus(): VoiceCallStatus = VoiceCallStatus.NOT_CONFIGURED
}
