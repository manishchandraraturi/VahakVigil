package com.manish.vahakvigil.domain.analysis

import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventAnalysisCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.model.SensorRecordingAnalysis
import com.manish.vahakvigil.domain.model.SensorStreamAnalysis
import com.manish.vahakvigil.domain.model.SensorStreamType
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class SensorAnalysisProgress(val processedEvents: Long, val totalEvents: Long, val pass: Int) {
    val fraction: Float get() = if (totalEvents <= 0L) 1f else ((processedEvents.toDouble() / (totalEvents * 2.0)).toFloat()).coerceIn(0f, 1f)
}

/** Two bounded keyset passes: statistics first, then min/max chart buckets and gap counts. */
class RideSensorAnalysisLoader(
    private val repository: SensorEventRepository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val maxChartBuckets: Int = SensorTimelineAccumulator.DEFAULT_MAX_BUCKETS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    init {
        require(pageSize > 0)
        require(maxChartBuckets > 0)
    }

    suspend fun load(
        sessionId: String,
        onProgress: (SensorAnalysisProgress) -> Unit = {}
    ): SensorRecordingAnalysis = withContext(dispatcher) {
        val snapshot = repository.getExportSnapshot(sessionId)
        if (snapshot.eventCount == 0L) return@withContext SensorRecordingAnalysis(0, null, null)

        val accumulators = mapOf(
            RawSensorType.ACCELEROMETER to SensorStreamAccumulator(SensorStreamType.ACCELEROMETER),
            RawSensorType.GYROSCOPE to SensorStreamAccumulator(SensorStreamType.GYROSCOPE)
        )
        forEachSnapshotEvent(sessionId, snapshot) { row, processed ->
            accumulators.getValue(row.event.type).add(row.event)
            onProgress(SensorAnalysisProgress(processed, snapshot.eventCount, 1))
        }

        val stats = accumulators.mapValues { (_, accumulator) -> accumulator.finish() }
        val timelines = stats.mapNotNull { (type, summary) ->
            summary?.let {
                type to SensorTimelineAccumulator(
                    type = summary.type,
                    startTimestampNanos = summary.minimumTimestampNanos,
                    endTimestampNanos = summary.maximumTimestampNanos,
                    eventCount = summary.eventCount,
                    maxBuckets = maxChartBuckets
                )
            }
        }.toMap()
        val previousTimestamp = mutableMapOf<RawSensorType, Long>()
        val gapCounts = mutableMapOf<RawSensorType, Long>(RawSensorType.ACCELEROMETER to 0L, RawSensorType.GYROSCOPE to 0L)
        forEachSnapshotEvent(sessionId, snapshot) { row, processed ->
            val event = row.event
            val accumulator = accumulators.getValue(event.type)
            val medianNanos = accumulator.medianIntervalNanos()
            val prior = previousTimestamp.put(event.type, event.timestampNanos)
            val delta = prior?.let { event.timestampNanos - it }
            if (delta != null && delta > 0L && medianNanos != null && delta > medianNanos * IRREGULAR_GAP_MEDIAN_MULTIPLIER) {
                gapCounts[event.type] = gapCounts.getValue(event.type) + 1L
            }
            timelines[event.type]?.add(event)
            onProgress(SensorAnalysisProgress(snapshot.eventCount + processed, snapshot.eventCount, 2))
        }

        fun complete(type: RawSensorType): SensorStreamAnalysis? {
            val summary = stats.getValue(type) ?: return null
            return accumulators.getValue(type).finish(
                irregularGapCount = gapCounts.getValue(type),
                timeline = timelines[type]?.finish().orEmpty()
            ) ?: summary
        }
        SensorRecordingAnalysis(
            totalEventCount = snapshot.eventCount,
            accelerometer = complete(RawSensorType.ACCELEROMETER),
            gyroscope = complete(RawSensorType.GYROSCOPE)
        )
    }

    private suspend fun forEachSnapshotEvent(
        sessionId: String,
        snapshot: SensorEventExportSnapshot,
        consume: (suspend (RecordedSensorEvent, Long) -> Unit)
    ) {
        var cursor = SensorEventAnalysisCursor(eventId = 0L)
        var processed = 0L
        while (processed < snapshot.eventCount) {
            coroutineContext.ensureActive()
            val page = repository.getAnalysisPage(sessionId, snapshot.maxEventId, cursor, pageSize)
            if (page.isEmpty()) throw IllegalStateException("Sensor recording changed while analysis was running")
            for (row in page) {
                coroutineContext.ensureActive()
                if (row.eventId <= cursor.eventId || row.eventId > snapshot.maxEventId) {
                    throw IllegalStateException("Sensor analysis received an invalid event cursor")
                }
                consume(row, processed + 1L)
                processed++
            }
            cursor = SensorEventAnalysisCursor(page.last().eventId)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 500
        const val IRREGULAR_GAP_MEDIAN_MULTIPLIER = 3.0
    }
}
