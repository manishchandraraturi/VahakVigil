package com.manish.vahakvigil.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.manish.vahakvigil.data.local.db.entity.EmergencyIncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EmergencyIncidentDao {
    @Query("SELECT * FROM emergency_incidents ORDER BY incidentTimestampEpochMillis DESC")
    abstract fun observeIncidents(): Flow<List<EmergencyIncidentEntity>>

    @Query("SELECT * FROM emergency_incidents WHERE incidentId = :incidentId LIMIT 1")
    abstract fun observeIncident(incidentId: String): Flow<EmergencyIncidentEntity?>

    @Query("SELECT * FROM emergency_incidents WHERE incidentId = :incidentId LIMIT 1")
    abstract suspend fun getIncident(incidentId: String): EmergencyIncidentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(entity: EmergencyIncidentEntity): Long

    @Update
    abstract suspend fun update(entity: EmergencyIncidentEntity)

    @Transaction
    open suspend fun recordPreservingEvidence(entity: EmergencyIncidentEntity) {
        val existing = getIncident(entity.incidentId)
        if (existing == null) insertIfMissing(entity)
        else update(entity.copy(voiceNote = existing.voiceNote, photoReference = existing.photoReference))
    }

    @Query("UPDATE emergency_incidents SET voiceNote = :note, photoReference = :photo WHERE incidentId = :incidentId")
    abstract suspend fun updateEvidence(incidentId: String, note: String?, photo: String?): Int

    @Query("DELETE FROM emergency_incidents WHERE incidentId = :incidentId")
    abstract suspend fun deleteIncident(incidentId: String): Int
}
