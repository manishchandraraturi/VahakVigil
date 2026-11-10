package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.EmergencyDispatchAttemptState
import com.manish.vahakvigil.domain.model.EmergencyLifecycleRecord
import com.manish.vahakvigil.domain.model.EmergencyLifecyclePhase
import com.manish.vahakvigil.domain.model.EmergencyUserResponse

interface EmergencyLifecycleRepository {
    suspend fun getLatest(): EmergencyLifecycleRecord?
    suspend fun get(incidentId: String): EmergencyLifecycleRecord?
    suspend fun createIfMissing(record: EmergencyLifecycleRecord)
    suspend fun save(record: EmergencyLifecycleRecord)
    /** Atomically claims the only automatic attempt; retry is permitted only from a known FAILED result. */
    suspend fun beginDispatchAttempt(incidentId: String, allowFailedRetry: Boolean, attemptedAtEpochMillis: Long): Boolean
    suspend fun updateOutcome(incidentId: String, phase: EmergencyLifecyclePhase, state: EmergencyDispatchAttemptState, resolvedAtEpochMillis: Long)
    suspend fun updatePhaseAndResponse(incidentId: String, phase: EmergencyLifecyclePhase, response: EmergencyUserResponse)
    suspend fun updateLocation(incidentId: String, latitude: Double?, longitude: Double?, timestampEpochMillis: Long?)
}
