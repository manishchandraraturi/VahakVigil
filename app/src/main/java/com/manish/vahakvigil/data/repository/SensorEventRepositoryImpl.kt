package com.manish.vahakvigil.data.repository

import com.manish.vahakvigil.data.local.db.dao.SensorEventDao
import com.manish.vahakvigil.data.local.db.entity.SensorEventEntity
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventAnalysisCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.repository.SensorEventRepository

class SensorEventRepositoryImpl(
    private val dao: SensorEventDao
) : SensorEventRepository {
    override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) {
        if (events.isEmpty()) return
        dao.insertBatch(events.map { it.toEntity(sessionId) })
    }

    override suspend fun getEventsForSession(
        sessionId: String,
        limit: Int,
        offset: Int
    ): List<RawSensorEvent> {
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must not be negative" }
        return dao.getEventsForSession(sessionId, limit, offset).map { it.toRawEvent() }
    }

    override suspend fun countForSession(sessionId: String): Long = dao.countForSession(sessionId)

    override suspend fun getExportSnapshot(sessionId: String): SensorEventExportSnapshot =
        dao.getExportSnapshot(sessionId)

    override suspend fun getExportPage(
        sessionId: String,
        maxEventId: Long,
        after: SensorEventExportCursor?,
        limit: Int
    ): List<RecordedSensorEvent> {
        require(limit > 0) { "limit must be positive" }
        return dao.getExportPage(
            sessionId = sessionId,
            maxEventId = maxEventId,
            afterTimestampNanos = after?.timestampNanos,
            afterEventId = after?.eventId,
            limit = limit
        ).map { row -> RecordedSensorEvent(row.eventId, row.toRawEvent()) }
    }

    override suspend fun getAnalysisPage(
        sessionId: String,
        maxEventId: Long,
        after: SensorEventAnalysisCursor,
        limit: Int
    ): List<RecordedSensorEvent> {
        require(limit > 0) { "limit must be positive" }
        return dao.getAnalysisPage(sessionId, maxEventId, after.eventId, limit)
            .map { row -> RecordedSensorEvent(row.eventId, row.toRawEvent()) }
    }

    private fun RawSensorEvent.toEntity(sessionId: String) = SensorEventEntity(
        sessionId = sessionId,
        sensorType = type.name,
        timestampNanos = timestampNanos,
        timestampEpochMillis = timestampEpochMillis,
        x = x,
        y = y,
        z = z,
        accuracy = accuracy
    )

    private fun SensorEventEntity.toRawEvent() = RawSensorEvent(
        type = RawSensorType.valueOf(sensorType),
        timestampNanos = timestampNanos,
        timestampEpochMillis = timestampEpochMillis,
        x = x,
        y = y,
        z = z,
        accuracy = accuracy
    )
}
