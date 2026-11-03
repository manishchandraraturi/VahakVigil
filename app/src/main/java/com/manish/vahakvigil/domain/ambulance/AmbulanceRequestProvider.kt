package com.manish.vahakvigil.domain.ambulance

import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyHospital
import com.manish.vahakvigil.domain.model.EmergencyLocation

/**
 * Lifecycle states of an ambulance request.
 */
enum class AmbulanceRequestStatus {
    /** Ambulance integration/API is not configured. */
    NOT_CONFIGURED,

    /** Request transmitted to dispatch authority/fleet. */
    REQUESTED,

    /** Dispatch request accepted by dispatch authority. */
    ACCEPTED,

    /** Request rejected or unavailable in geographic sector. */
    REJECTED,

    /** Request attempt failed due to network or provider error. */
    FAILED,

    /** Status unknown. */
    UNKNOWN
}

/**
 * Result of an ambulance request attempt.
 */
data class AmbulanceRequestResult(
    val status: AmbulanceRequestStatus,
    val details: String = ""
)

/**
 * Pure Kotlin contract for requesting ambulance dispatch during an emergency.
 */
interface AmbulanceRequestProvider {

    /**
     * Submits an ambulance dispatch request.
     */
    suspend fun requestAmbulance(
        location: EmergencyLocation,
        hospital: EmergencyHospital?,
        payload: EmergencyDispatchPayload
    ): AmbulanceRequestResult

    /**
     * Retrieves the current status of an in-flight or completed ambulance request.
     */
    fun getRequestStatus(): AmbulanceRequestStatus
}

/**
 * Safe unconfigured implementation of [AmbulanceRequestProvider].
 *
 * Prevents false claims of ambulance dispatch when no integration is active.
 */
class UnconfiguredAmbulanceRequestProvider : AmbulanceRequestProvider {
    override suspend fun requestAmbulance(
        location: EmergencyLocation,
        hospital: EmergencyHospital?,
        payload: EmergencyDispatchPayload
    ): AmbulanceRequestResult {
        return AmbulanceRequestResult(
            status = AmbulanceRequestStatus.NOT_CONFIGURED,
            details = "Ambulance request service not configured."
        )
    }

    override fun getRequestStatus(): AmbulanceRequestStatus = AmbulanceRequestStatus.NOT_CONFIGURED
}
