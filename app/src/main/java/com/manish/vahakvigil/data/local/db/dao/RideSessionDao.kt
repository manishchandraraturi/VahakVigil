package com.manish.vahakvigil.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.manish.vahakvigil.data.local.db.entity.RideSessionEntity
import com.manish.vahakvigil.data.local.db.entity.RideSessionPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RideSessionDao {
    @Query("SELECT * FROM ride_sessions ORDER BY startedAtEpochMillis DESC")
    abstract fun observeSessions(): Flow<List<RideSessionEntity>>

    @Query("SELECT * FROM ride_sessions WHERE id = :id LIMIT 1")
    abstract fun observeSession(id: String): Flow<RideSessionEntity?>

    @Query("SELECT * FROM ride_sessions WHERE id = :id LIMIT 1")
    abstract suspend fun getSession(id: String): RideSessionEntity?

    @Query("SELECT * FROM ride_sessions WHERE status = 'ACTIVE' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    abstract suspend fun getActiveSession(): RideSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSession(session: RideSessionEntity): Long

    @Transaction
    open suspend fun ensureActiveSession(candidate: RideSessionEntity): RideSessionEntity {
        getActiveSession()?.let { return it }
        insertSession(candidate)
        return getActiveSession() ?: candidate
    }

    @Insert
    abstract suspend fun insertPoint(point: RideSessionPointEntity): Long

    @Query("UPDATE ride_sessions SET startLatitude = COALESCE(startLatitude, :latitude), startLongitude = COALESCE(startLongitude, :longitude), endLatitude = :latitude, endLongitude = :longitude WHERE id = :sessionId AND status = 'ACTIVE'")
    abstract suspend fun updateObservedEndpoint(sessionId: String, latitude: Double, longitude: Double): Int

    @Query("SELECT * FROM ride_session_points WHERE sessionId = :sessionId ORDER BY timestampEpochMillis ASC, pointId ASC")
    abstract suspend fun getPoints(sessionId: String): List<RideSessionPointEntity>

    @Query("SELECT * FROM ride_session_points WHERE sessionId = :sessionId ORDER BY timestampEpochMillis DESC, pointId DESC LIMIT 1")
    abstract suspend fun getLastPoint(sessionId: String): RideSessionPointEntity?

    @Query("UPDATE ride_sessions SET endedAtEpochMillis = :endedAt, durationMillis = :duration, distanceMeters = :distance, averageSpeedMetersPerSecond = :averageSpeed, maximumSpeedMetersPerSecond = :maximumSpeed, status = :status WHERE id = :sessionId AND status = 'ACTIVE'")
    abstract suspend fun finalizeActiveSession(
        sessionId: String,
        endedAt: Long,
        duration: Long,
        distance: Double?,
        averageSpeed: Double?,
        maximumSpeed: Double?,
        status: String
    ): Int

    @Query("DELETE FROM ride_sessions WHERE id = :sessionId")
    abstract suspend fun deleteSession(sessionId: String): Int
}
