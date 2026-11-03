package com.manish.vahakvigil.domain.hospital

import com.manish.vahakvigil.domain.model.EmergencyHospital

/**
 * Result status of hospital communication actions.
 */
enum class HospitalCallStatus {
    /** Hospital phone number is not available. */
    NOT_AVAILABLE,

    /** Telephony/calling provider is not configured. */
    NOT_CONFIGURED,

    /** Phone dialer intent requested on Android system. */
    DIAL_REQUESTED,

    /** Call active / dialing in progress. */
    CALLING,

    /** Call connected with recipient. */
    CONNECTED,

    /** Call completed. */
    COMPLETED,

    /** Call initiation or execution failed. */
    FAILED,

    /** Status unknown. */
    UNKNOWN
}

/**
 * Result of attempting to communicate with an emergency hospital.
 */
data class HospitalCallResult(
    val status: HospitalCallStatus,
    val details: String = ""
)

/**
 * Pure Kotlin abstraction for initiating direct communication with an emergency hospital.
 */
interface EmergencyHospitalCommunicationProvider {

    /**
     * Initiates communication with [hospital].
     *
     * @return [HospitalCallResult] indicating immediate initiation state (e.g. [HospitalCallStatus.DIAL_REQUESTED]).
     */
    suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult

    /**
     * Current status of ongoing hospital communication.
     */
    fun getCommunicationStatus(): HospitalCallStatus
}

/**
 * Safe unconfigured/default implementation of [EmergencyHospitalCommunicationProvider].
 */
class UnconfiguredHospitalCommunicationProvider : EmergencyHospitalCommunicationProvider {
    override suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult {
        return if (hospital.phoneNumber.isNullOrBlank()) {
            HospitalCallResult(HospitalCallStatus.NOT_AVAILABLE, "Hospital phone unavailable.")
        } else {
            HospitalCallResult(HospitalCallStatus.NOT_CONFIGURED, "Hospital telephony provider not configured.")
        }
    }

    override fun getCommunicationStatus(): HospitalCallStatus = HospitalCallStatus.NOT_CONFIGURED
}
