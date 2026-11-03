package com.manish.vahakvigil.data.repository

import com.manish.vahakvigil.data.local.db.dao.EmergencyIncidentDao
import com.manish.vahakvigil.data.local.db.entity.EmergencyIncidentEntity
import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
import com.manish.vahakvigil.domain.repository.EmergencyIncidentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EmergencyIncidentRepositoryImpl(private val dao: EmergencyIncidentDao) : EmergencyIncidentRepository {
    override fun observeIncidents(): Flow<List<EmergencyIncidentRecord>> =
        dao.observeIncidents().map { rows -> rows.map { it.toDomain() } }

    override fun observeIncident(incidentId: String): Flow<EmergencyIncidentRecord?> =
        dao.observeIncident(incidentId).map { it?.toDomain() }

    override suspend fun getIncident(incidentId: String): EmergencyIncidentRecord? = dao.getIncident(incidentId)?.toDomain()

    override suspend fun recordIncident(record: EmergencyIncidentRecord) {
        dao.recordPreservingEvidence(record.toEntity())
    }

    override suspend fun updateEvidence(incidentId: String, note: String?, photoReference: String?) {
        dao.updateEvidence(incidentId, note?.trim()?.takeIf(String::isNotBlank), photoReference)
    }

    override suspend fun deleteIncident(incidentId: String) {
        dao.deleteIncident(incidentId)
    }

    private fun EmergencyIncidentEntity.toDomain() = EmergencyIncidentRecord(
        incidentId, incidentTimestampEpochMillis, windowStartEpochMillis, windowEndEpochMillis,
        latitude, longitude, confidenceScore, evidenceSummary, status, userAction, voiceNote, photoReference
    )

    private fun EmergencyIncidentRecord.toEntity() = EmergencyIncidentEntity(
        incidentId, incidentTimestampEpochMillis, windowStartEpochMillis, windowEndEpochMillis,
        latitude, longitude, confidenceScore, evidenceSummary, status, userAction, voiceNote, photoReference
    )
}
