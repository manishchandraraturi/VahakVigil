package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot

/** Persistent raw sensor events scoped to an existing ride session. */
interface SensorEventRepository {
    suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>)

    suspend fun getEventsForSession(
        sessionId: String,
        limit: Int,
        offset: Int
    ): List<RawSensorEvent>

    suspend fun countForSession(sessionId: String): Long

    suspend fun getExportSnapshot(sessionId: String): SensorEventExportSnapshot

    suspend fun getExportPage(
        sessionId: String,
        maxEventId: Long,
        after: SensorEventExportCursor?,
        limit: Int
    ): List<RecordedSensorEvent>
}
