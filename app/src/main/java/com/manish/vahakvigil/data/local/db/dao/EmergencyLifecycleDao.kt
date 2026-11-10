package com.manish.vahakvigil.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.manish.vahakvigil.data.local.db.entity.EmergencyLifecycleEntity

@Dao
abstract class EmergencyLifecycleDao {
    @Query("SELECT * FROM emergency_lifecycle ORDER BY incidentCreatedAtEpochMillis DESC LIMIT 1")
    abstract suspend fun getLatest(): EmergencyLifecycleEntity?

    @Query("SELECT * FROM emergency_lifecycle WHERE incidentId = :incidentId LIMIT 1")
    abstract suspend fun get(incidentId: String): EmergencyLifecycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun save(entity: EmergencyLifecycleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(entity: EmergencyLifecycleEntity): Long

    @Query("UPDATE emergency_lifecycle SET dispatchState = 'ATTEMPTED', phase = 'DISPATCHING', dispatchAttemptCount = dispatchAttemptCount + 1, dispatchAttemptedAtEpochMillis = :attemptedAt, dispatchResolvedAtEpochMillis = NULL WHERE incidentId = :incidentId AND dispatchState = 'NOT_STARTED'")
    abstract suspend fun beginFirstDispatchAttempt(incidentId: String, attemptedAt: Long): Int

    @Query("UPDATE emergency_lifecycle SET dispatchState = 'ATTEMPTED', phase = 'DISPATCHING', dispatchAttemptCount = dispatchAttemptCount + 1, dispatchAttemptedAtEpochMillis = :attemptedAt, dispatchResolvedAtEpochMillis = NULL WHERE incidentId = :incidentId AND dispatchState = 'FAILED'")
    abstract suspend fun beginFailedRetry(incidentId: String, attemptedAt: Long): Int

    @Query("UPDATE emergency_lifecycle SET phase = :phase, dispatchState = :state, dispatchResolvedAtEpochMillis = :resolvedAt WHERE incidentId = :incidentId AND dispatchState = 'ATTEMPTED'")
    abstract suspend fun updateOutcome(incidentId: String, phase: String, state: String, resolvedAt: Long): Int

    @Query("UPDATE emergency_lifecycle SET phase = :phase, userResponse = :response WHERE incidentId = :incidentId")
    abstract suspend fun updatePhaseAndResponse(incidentId: String, phase: String, response: String): Int

    @Query("UPDATE emergency_lifecycle SET lastLatitude = :latitude, lastLongitude = :longitude, lastLocationTimestampEpochMillis = :timestamp WHERE incidentId = :incidentId")
    abstract suspend fun updateLocation(incidentId: String, latitude: Double?, longitude: Double?, timestamp: Long?): Int

    @Transaction
    open suspend fun beginDispatchAttempt(incidentId: String, allowFailedRetry: Boolean, attemptedAt: Long): Boolean {
        val changed = if (allowFailedRetry) beginFailedRetry(incidentId, attemptedAt)
        else beginFirstDispatchAttempt(incidentId, attemptedAt)
        return changed == 1
    }
}
