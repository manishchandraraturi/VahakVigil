package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
import kotlinx.coroutines.flow.Flow

interface EmergencyIncidentRepository {
    fun observeIncidents(): Flow<List<EmergencyIncidentRecord>>
    fun observeIncident(incidentId: String): Flow<EmergencyIncidentRecord?>
    suspend fun getIncident(incidentId: String): EmergencyIncidentRecord?
    suspend fun recordIncident(record: EmergencyIncidentRecord)
    suspend fun updateEvidence(incidentId: String, note: String?, photoReference: String?)
    suspend fun deleteIncident(incidentId: String)
}
