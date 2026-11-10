package com.manish.vahakvigil.data.repository

import com.manish.vahakvigil.data.local.db.dao.EmergencyLifecycleDao
import com.manish.vahakvigil.data.local.db.entity.EmergencyLifecycleEntity
import com.manish.vahakvigil.domain.model.EmergencyDispatchAttemptState
import com.manish.vahakvigil.domain.model.EmergencyLifecyclePhase
import com.manish.vahakvigil.domain.model.EmergencyLifecycleRecord
import com.manish.vahakvigil.domain.model.EmergencyUserResponse
import com.manish.vahakvigil.domain.repository.EmergencyLifecycleRepository

class EmergencyLifecycleRepositoryImpl(private val dao: EmergencyLifecycleDao) : EmergencyLifecycleRepository {
    override suspend fun getLatest() = dao.getLatest()?.toDomain()
    override suspend fun get(incidentId: String) = dao.get(incidentId)?.toDomain()
    override suspend fun save(record: EmergencyLifecycleRecord) = dao.save(record.toEntity())
    override suspend fun createIfMissing(record: EmergencyLifecycleRecord) { dao.insertIfMissing(record.toEntity()) }

    override suspend fun beginDispatchAttempt(incidentId: String, allowFailedRetry: Boolean, attemptedAtEpochMillis: Long) =
        dao.beginDispatchAttempt(incidentId, allowFailedRetry, attemptedAtEpochMillis)

    override suspend fun updateOutcome(incidentId: String, phase: EmergencyLifecyclePhase, state: EmergencyDispatchAttemptState, resolvedAtEpochMillis: Long) {
        dao.updateOutcome(incidentId, phase.name, state.name, resolvedAtEpochMillis)
    }

    override suspend fun updatePhaseAndResponse(incidentId: String, phase: EmergencyLifecyclePhase, response: EmergencyUserResponse) {
        dao.updatePhaseAndResponse(incidentId, phase.name, response.name)
    }

    override suspend fun updateLocation(incidentId: String, latitude: Double?, longitude: Double?, timestampEpochMillis: Long?) {
        dao.updateLocation(incidentId, latitude, longitude, timestampEpochMillis)
    }

    private fun EmergencyLifecycleEntity.toDomain() = EmergencyLifecycleRecord(
        incidentId, incidentCreatedAtEpochMillis, windowStartEpochMillis, windowEndEpochMillis, confidenceScore,
        EmergencyLifecyclePhase.valueOf(phase), deadlineElapsedRealtimeMillis, deadlineEpochMillis, bootCount,
        EmergencyUserResponse.valueOf(userResponse), EmergencyDispatchAttemptState.valueOf(dispatchState),
        dispatchAttemptCount, dispatchAttemptedAtEpochMillis, dispatchResolvedAtEpochMillis,
        lastLatitude, lastLongitude, lastLocationTimestampEpochMillis
    )

    private fun EmergencyLifecycleRecord.toEntity() = EmergencyLifecycleEntity(
        incidentId, incidentCreatedAtEpochMillis, windowStartEpochMillis, windowEndEpochMillis, confidenceScore,
        phase.name, deadlineElapsedRealtimeMillis, deadlineEpochMillis, bootCount, userResponse.name,
        dispatchState.name, dispatchAttemptCount, dispatchAttemptedAtEpochMillis, dispatchResolvedAtEpochMillis,
        lastLatitude, lastLongitude, lastLocationTimestampEpochMillis
    )
}
