package com.manish.vahakvigil.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import com.manish.vahakvigil.domain.export.DatasetContributionConsent
import com.manish.vahakvigil.domain.export.DatasetPackageExportResult
import com.manish.vahakvigil.domain.export.DatasetPackageProgress
import com.manish.vahakvigil.domain.export.SensorCsvExportProgress
import com.manish.vahakvigil.domain.export.SensorCsvExportResult
import com.manish.vahakvigil.domain.export.SensorCsvExporter
import com.manish.vahakvigil.domain.export.SensorDatasetPackageExport
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Creates an offline ZIP containing only pseudonymous sensor rows and allowlisted metadata. */
class AndroidSensorDatasetPackageExporter(
    context: Context? = null,
    private val contentResolver: ContentResolver? = null,
    private val sensorEvents: SensorEventRepository,
    private val rides: RideSessionRepository,
    private val csvExporter: SensorCsvExporter,
    private val consent: DatasetContributionConsent,
    private val now: () -> Long = System::currentTimeMillis,
    private val newDatasetId: () -> String = { UUID.randomUUID().toString() },
    private val deviceModelProvider: () -> String? = { Build.MODEL?.takeIf(String::isNotBlank) },
    private val appVersionProvider: () -> String = {
        context?.applicationContext?.let { ctx ->
            runCatching {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
            }.getOrDefault("unknown")
        } ?: "unknown"
    },
    private val openOutputStream: (String) -> OutputStream? = { destination ->
        contentResolver?.openOutputStream(Uri.parse(destination), "w")
    },
    private val deleteDestination: (String) -> Boolean = { destination ->
        runCatching {
            if (destination.startsWith("content://")) {
                val uri = Uri.parse(destination)
                contentResolver?.let { resolver ->
                    android.provider.DocumentsContract.deleteDocument(resolver, uri)
                } ?: false
            } else if (destination.startsWith("file://")) {
                val uri = Uri.parse(destination)
                val path = uri.path ?: destination.removePrefix("file://")
                val file = java.io.File(path)
                if (file.exists()) file.delete() else false
            } else {
                val file = java.io.File(destination)
                if (file.exists()) file.delete() else false
            }
        }.getOrDefault(false)
    }
) : SensorDatasetPackageExport {

    override suspend fun export(
        internalSessionId: String,
        destination: String,
        recordingProvenance: String,
        onProgress: suspend (DatasetPackageProgress) -> Unit
    ): DatasetPackageExportResult = withContext(Dispatchers.IO) {
        consent.requireConsent()
        require(recordingProvenance in PROVENANCE) { "Recording provenance is invalid" }
        val session = rides.getSession(internalSessionId) ?: throw IllegalArgumentException("Ride recording is unavailable")
        require(session.status != com.manish.vahakvigil.domain.model.RideSessionStatus.ACTIVE) { "Active rides cannot be exported" }
        val datasetId = newDatasetId()
        require(runCatching { UUID.fromString(datasetId) }.isSuccess) { "Dataset ID generator must return a random UUID" }
        require(datasetId != internalSessionId) { "Public dataset ID must differ from internal session ID" }
        val snapshot = sensorEvents.getExportSnapshot(internalSessionId)
        if (snapshot.eventCount == 0L) {
            deleteDestination(destination)
            return@withContext DatasetPackageExportResult.Empty
        }
        try {
            val quality = inspectRecording(internalSessionId, snapshot.eventCount, snapshot.maxEventId)
            val consentTime = consentStoreTime()
            val appVersion = appVersionProvider()
            val deviceModel = deviceModelProvider()
            val manifest = manifestJson(
                datasetId = datasetId,
                createdAt = now(),
                consentAt = consentTime,
                appVersion = appVersion,
                deviceModel = deviceModel,
                recordingStart = session.startedAtEpochMillis,
                recordingEnd = session.endedAtEpochMillis,
                provenance = recordingProvenance,
                quality = quality
            )
            val raw = openOutputStream(destination)
                ?: throw IOException("The selected destination could not be opened")
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry(CSV_ENTRY))
                val csvResult = csvExporter.export(
                    sessionId = internalSessionId,
                    openOutputStream = { NonClosingOutputStream(zip) },
                    csvSessionIdentifier = datasetId,
                    onProgress = { progress ->
                        currentCoroutineContext().ensureActive()
                        consent.requireConsent()
                        onProgress(DatasetPackageProgress(progress.writtenRows, progress.totalRows))
                    }
                )
                if (csvResult !is SensorCsvExportResult.Complete) throw IOException("Sensor recording became unavailable during export")
                zip.closeEntry()
                currentCoroutineContext().ensureActive()
                consent.requireConsent()
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.finish()
            }
            DatasetPackageExportResult.Complete(datasetId, snapshot.eventCount)
        } catch (t: Throwable) {
            deleteDestination(destination)
            throw t
        }
    }

    private suspend fun inspectRecording(sessionId: String, count: Long, maxEventId: Long): RecordingQuality {
        val digest = MessageDigest.getInstance("SHA-256")
        var cursor: SensorEventExportCursor? = null
        val counts = linkedMapOf<String, Long>()
        val previous = mutableMapOf<String, Long>()
        val intervalSums = mutableMapOf<String, Long>()
        val intervalCounts = mutableMapOf<String, Long>()
        var duplicates = 0L
        var nonMonotonic = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            consent.requireConsent()
            val page = sensorEvents.getExportPage(sessionId, maxEventId, cursor, 500)
            if (page.isEmpty()) break
            for (row in page) {
                val canonical = "${row.event.type.name},${row.event.timestampNanos},${row.event.timestampEpochMillis},${row.event.x},${row.event.y},${row.event.z},${row.event.accuracy}\n"
                digest.update(canonical.toByteArray(Charsets.UTF_8))
                val type = row.event.type.name
                counts[type] = (counts[type] ?: 0L) + 1
                val old = previous.put(type, row.event.timestampNanos)
                if (old != null) {
                    val delta = row.event.timestampNanos - old
                    if (delta == 0L) duplicates++
                    if (delta < 0L) nonMonotonic++ else if (delta > 0L) {
                        intervalSums[type] = (intervalSums[type] ?: 0L) + delta
                        intervalCounts[type] = (intervalCounts[type] ?: 0L) + 1L
                    }
                }
            }
            val last = page.last()
            cursor = SensorEventExportCursor(last.event.timestampNanos, last.eventId)
        }
        val meanFrequencies = counts.keys.associateWith { type ->
            intervalSums[type]?.takeIf { it > 0L }?.let { sum ->
                val samples = intervalCounts[type] ?: 0L
                if (samples > 0) samples * 1_000_000_000.0 / sum else null
            }
        }
        val contentHash = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
        return RecordingQuality(count, counts, duplicates, nonMonotonic, meanFrequencies, "per-ride dropped counts are not persisted", contentHash)
    }

    private fun consentStoreTime(): Long = consent.grantedAtEpochMillis() ?: 0L

    private data class RecordingQuality(
        val eventCount: Long,
        val sensorCounts: Map<String, Long>,
        val duplicateTimestamps: Long,
        val nonMonotonicTimestamps: Long,
        val meanSamplingFrequencyHz: Map<String, Double?>,
        val droppedUnavailableReason: String,
        val contentHash: String
    )

    private fun manifestJson(
        datasetId: String, createdAt: Long, consentAt: Long, appVersion: String, deviceModel: String?,
        recordingStart: Long, recordingEnd: Long?, provenance: String, quality: RecordingQuality
    ) = buildString {
        append("{\"package_schema_version\":1,\"dataset_session_id\":").append(json(datasetId))
        append(",\"app_version\":").append(json(appVersion))
        append(",\"device_model\":").append(deviceModel?.let(::json) ?: "null")
        append(",\"recording_start_epoch_millis\":").append(recordingStart)
        append(",\"recording_end_epoch_millis\":").append(recordingEnd ?: "null")
        append(",\"recording_provenance\":").append(json(provenance))
        append(",\"sensor_content_hash\":").append(json(quality.contentHash))
        append(",\"sensor_types\":[").append(quality.sensorCounts.keys.joinToString(",") { json(it) }).append(']')
        append(",\"sampling\":{")
        append(quality.sensorCounts.entries.joinToString(",") { (type, number) ->
            val hz = quality.meanSamplingFrequencyHz[type]
            "${json(type)}:{\"event_count\":$number,\"mean_sampling_frequency_hz\":${hz ?: "null"}}"
        }).append('}')
        append(",\"recording_quality\":{\"event_count\":").append(quality.eventCount)
        append(",\"duplicate_timestamp_events\":").append(quality.duplicateTimestamps)
        append(",\"non_monotonic_timestamps\":").append(quality.nonMonotonicTimestamps)
        append(",\"dropped_samples\":null,\"dropped_samples_unavailable_reason\":").append(json(quality.droppedUnavailableReason)).append('}')
        append(",\"consent\":{\"explicit\":true,\"consented_at_epoch_millis\":").append(consentAt)
        append(",\"exported_at_epoch_millis\":").append(createdAt).append(",\"export_is_user_initiated\":true}")
        append(",\"files\":[\"recording.csv\"]}")
    }

    private fun json(value: String): String = buildString {
        append('"'); value.forEach { char -> when (char) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        } }; append('"')
    }

    private class NonClosingOutputStream(output: ZipOutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }

    companion object {
        const val CSV_ENTRY = "recording.csv"
        const val MANIFEST_ENTRY = "dataset_manifest.json"
        private val PROVENANCE = setOf("REAL", "SIMULATED", "MIXED", "UNKNOWN")
    }
}
