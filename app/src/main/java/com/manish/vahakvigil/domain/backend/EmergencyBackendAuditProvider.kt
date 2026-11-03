package com.manish.vahakvigil.domain.backend

import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyResponseState

/**
 * Status of backend / cloud synchronization for emergency audit records.
 */
enum class BackendSyncStatus {
    /** Backend integration is unconfigured; running purely offline. */
    NOT_CONFIGURED,

    /** Incident event recorded safely on secure backend. */
    RECORDED,

    /** Cloud recording attempt failed; local operations continue unaffected. */
    FAILED
}

/**
 * Result of syncing an emergency event to the backend.
 */
data class BackendSyncResult(
    val status: BackendSyncStatus,
    val details: String = ""
)

/**
 * Contract for logging and auditing emergency dispatch events on a secure backend.
 *
 * Guarantees:
 * - Failure of cloud backend MUST NEVER block or fail the local emergency response.
 * - Sensitive user medical and location data must remain strictly isolated.
 */
interface EmergencyBackendAuditProvider {
    suspend fun recordEmergencyEvent(
        incidentTimestamp: Long,
        payload: EmergencyDispatchPayload,
        responseState: EmergencyResponseState
    ): BackendSyncResult
}

/**
 * Default local-only backend provider.
 */
class LocalOnlyBackendAuditProvider : EmergencyBackendAuditProvider {
    override suspend fun recordEmergencyEvent(
        incidentTimestamp: Long,
        payload: EmergencyDispatchPayload,
        responseState: EmergencyResponseState
    ): BackendSyncResult {
        return BackendSyncResult(
            status = BackendSyncStatus.NOT_CONFIGURED,
            details = "Cloud backend audit unconfigured; operating in local mode."
        )
    }
}
