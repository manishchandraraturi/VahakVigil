package com.manish.vahakvigil.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.manish.vahakvigil.data.local.db.entity.SensorEventEntity
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot

@Dao
interface SensorEventDao {
    @Insert
    suspend fun insertBatch(events: List<SensorEventEntity>): List<Long>

    @Query(
        "SELECT * FROM sensor_events WHERE sessionId = :sessionId " +
            "ORDER BY timestampNanos ASC, eventId ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int): List<SensorEventEntity>

    @Query("SELECT COUNT(*) AS eventCount, COALESCE(MAX(eventId), 0) AS maxEventId FROM sensor_events WHERE sessionId = :sessionId")
    suspend fun getExportSnapshot(sessionId: String): SensorEventExportSnapshot

    @Query(
        "SELECT * FROM sensor_events WHERE sessionId = :sessionId AND eventId <= :maxEventId " +
            "AND (:afterTimestampNanos IS NULL OR timestampNanos > :afterTimestampNanos " +
            "OR (timestampNanos = :afterTimestampNanos AND eventId > :afterEventId)) " +
            "ORDER BY timestampNanos ASC, eventId ASC LIMIT :limit"
    )
    suspend fun getExportPage(
        sessionId: String,
        maxEventId: Long,
        afterTimestampNanos: Long?,
        afterEventId: Long?,
        limit: Int
    ): List<SensorEventEntity>

    @Query("SELECT COUNT(*) FROM sensor_events WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Long
}
