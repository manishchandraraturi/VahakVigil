package com.manish.vahakvigil.domain.analysis

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventAnalysisCursor
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSensorAnalysisLoaderTest {
    @Test fun pagesBySessionAndSnapshotAndBoundsTimelineMemory() = runBlocking {
        val events = (1L..4_000L).map { id ->
            val type = if (id % 2L == 0L) RawSensorType.ACCELEROMETER else RawSensorType.GYROSCOPE
            RecordedSensorEvent(id, RawSensorEvent(type, id * 10_000_000L, 1_700_000_000_000L + id * 10L, id.toFloat(), -id.toFloat(), 1f, 3))
        }
        val repository = PagingRepository(events)
        val analysis = RideSensorAnalysisLoader(repository, pageSize = 137, maxChartBuckets = 32, dispatcher = Dispatchers.Unconfined)
            .load("ride-A")

        assertEquals(4_000L, analysis.totalEventCount)
        assertEquals(2_000L, analysis.accelerometer!!.eventCount)
        assertEquals(2_000L, analysis.gyroscope!!.eventCount)
        assertTrue(analysis.accelerometer.timeline.size <= 32)
        assertTrue(analysis.gyroscope.timeline.size <= 32)
        assertEquals(4_000, repository.requestedIds.distinct().size)
        assertTrue(repository.requestedLimits.all { it <= 137 })
        assertEquals(setOf("ride-A"), repository.requestedSessions.toSet())
    }

    @Test fun emptySnapshotReturnsNoSensorStreams() = runBlocking {
        val result = RideSensorAnalysisLoader(PagingRepository(emptyList()), dispatcher = Dispatchers.Unconfined).load("empty")
        assertEquals(0L, result.totalEventCount)
        assertEquals(null, result.accelerometer)
        assertEquals(null, result.gyroscope)
    }

    private class PagingRepository(private val rows: List<RecordedSensorEvent>) : SensorEventRepository {
        val requestedIds = mutableListOf<Long>()
        val requestedLimits = mutableListOf<Int>()
        val requestedSessions = mutableListOf<String>()
        override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) = Unit
        override suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int) = emptyList<RawSensorEvent>()
        override suspend fun countForSession(sessionId: String) = rows.size.toLong()
        override suspend fun getExportSnapshot(sessionId: String) = SensorEventExportSnapshot(rows.size.toLong(), rows.lastOrNull()?.eventId ?: 0L)
        override suspend fun getExportPage(sessionId: String, maxEventId: Long, after: SensorEventExportCursor?, limit: Int) = emptyList<RecordedSensorEvent>()
        override suspend fun getAnalysisPage(sessionId: String, maxEventId: Long, after: SensorEventAnalysisCursor, limit: Int): List<RecordedSensorEvent> {
            requestedSessions += sessionId
            requestedLimits += limit
            val page = rows.asSequence().filter { it.eventId > after.eventId && it.eventId <= maxEventId }.take(limit).toList()
            requestedIds += page.map { it.eventId }
            return page
        }
    }
}
