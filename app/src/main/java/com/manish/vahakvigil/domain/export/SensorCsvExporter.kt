package com.manish.vahakvigil.domain.export

import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class SensorCsvExportProgress(val writtenRows: Long, val totalRows: Long)

sealed interface SensorCsvExportResult {
    data object Empty : SensorCsvExportResult
    data class Complete(val exportedRows: Long) : SensorCsvExportResult
}

/** Streams a stable, selected-session snapshot to UTF-8 CSV in bounded pages. */
class SensorCsvExporter(
    private val repository: SensorEventRepository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }

    suspend fun countEvents(sessionId: String): Long = withContext(ioDispatcher) {
        repository.countForSession(sessionId)
    }

    /** [openOutputStream] is called only when the selected session has data; exporter owns the stream. */
    suspend fun export(
        sessionId: String,
        openOutputStream: () -> OutputStream,
        onProgress: suspend (SensorCsvExportProgress) -> Unit = {}
    ): SensorCsvExportResult = withContext(ioDispatcher) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        val snapshot = repository.getExportSnapshot(sessionId)
        if (snapshot.eventCount == 0L) return@withContext SensorCsvExportResult.Empty

        val output = openOutputStream()
        BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8), WRITER_BUFFER_SIZE).use { writer ->
            writer.write(CSV_HEADER)
            writer.write("\n")
            onProgress(SensorCsvExportProgress(0L, snapshot.eventCount))

            var cursor: SensorEventExportCursor? = null
            var written = 0L
            while (written < snapshot.eventCount) {
                coroutineContext.ensureActive()
                val page = repository.getExportPage(
                    sessionId = sessionId,
                    maxEventId = snapshot.maxEventId,
                    after = cursor,
                    limit = pageSize
                )
                if (page.isEmpty()) {
                    throw IllegalStateException("Sensor data changed during export; incomplete file was not reported as successful")
                }
                for (row in page) {
                    coroutineContext.ensureActive()
                    writer.write(row.toCsv(sessionId))
                    writer.write("\n")
                }
                val last = page.last()
                cursor = SensorEventExportCursor(last.event.timestampNanos, last.eventId)
                written += page.size
                writer.flush()
                onProgress(SensorCsvExportProgress(written.coerceAtMost(snapshot.eventCount), snapshot.eventCount))
            }
            SensorCsvExportResult.Complete(written)
        }
    }

    private fun RecordedSensorEvent.toCsv(sessionId: String): String = buildString(CSV_ROW_INITIAL_CAPACITY) {
        append(csvField(sessionId)); append(',')
        append(csvField(event.type.name)); append(',')
        append(event.timestampNanos); append(',')
        append(event.timestampEpochMillis); append(',')
        append(event.x.toString()); append(',')
        append(event.y.toString()); append(',')
        append(event.z.toString()); append(',')
        append(event.accuracy)
    }

    private fun csvField(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\r' || it == '\n' }) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEach { char ->
                if (char == '"') append("\"\"") else append(char)
            }
            append('"')
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 500
        private const val WRITER_BUFFER_SIZE = 16 * 1024
        private const val CSV_ROW_INITIAL_CAPACITY = 128
        /** Stable schema: one row per event, in this exact column order. */
        const val CSV_HEADER = "session_id,sensor_type,timestamp_nanos,timestamp_epoch_millis,x,y,z,accuracy"

        fun suggestedFilename(sessionId: String): String {
            val safeId = sessionId.map { char ->
                if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '-' || char == '_' || char == '.') char else '_'
            }.joinToString("").trim('.', '_', '-')
                .take(80)
                .ifBlank { "ride" }
            return "vahakvigil_sensor_session_$safeId.csv"
        }
    }
}
