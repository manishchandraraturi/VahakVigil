package com.manish.vahakvigil.domain.evaluation

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import java.util.Locale

enum class RecordingProvenance { OBSERVED, SYNTHETIC_TEST_FIXTURE }
enum class GroundTruthReviewStatus { REVIEWED, UNREVIEWED, NEEDS_REVIEW }
enum class CrashGroundTruthOutcome {
    CRASH_CONFIRMED, NO_CRASH_CONFIRMED, INDETERMINATE;

    companion object {
        @Deprecated("Use CRASH_CONFIRMED") val CRASH = CRASH_CONFIRMED
        @Deprecated("Use NO_CRASH_CONFIRMED") val NO_CRASH = NO_CRASH_CONFIRMED
    }
}
enum class GroundTruthRecordingOrigin { REAL, SIMULATED, MIXED, UNKNOWN }

/** Binary crash outcome is accepted only as explicit, separately reviewed ground truth. */
data class CrashGroundTruth(
    val outcome: CrashGroundTruthOutcome?,
    val reviewStatus: GroundTruthReviewStatus,
    val crashOnsetTimestampNanos: Long? = null,
    val sessionId: String? = null,
    val reviewId: String? = null,
    val reviewerId: String? = null,
    val reviewedAtUtc: String? = null,
    val evidenceProvenance: String? = null,
    val recordingOrigin: GroundTruthRecordingOrigin = GroundTruthRecordingOrigin.UNKNOWN,
    val notes: String? = null
) {
    init { require(crashOnsetTimestampNanos == null || crashOnsetTimestampNanos >= 0L) }
}

/** Shape used to transfer one validated Python manifest review into a Kotlin replay. */
data class ReviewedBinaryOutcomeAnnotation(
    val sessionId: String,
    val reviewId: String,
    val outcome: String,
    val reviewerId: String,
    val reviewedAtUtc: String,
    val evidenceProvenance: String,
    val recordingOrigin: String,
    val notes: String? = null,
    val crashOnsetTimestampNanos: Long? = null
) {
    fun toCrashGroundTruth(): CrashGroundTruth? {
        val parsedOutcome = runCatching { CrashGroundTruthOutcome.valueOf(outcome) }.getOrNull() ?: return null
        val origin = runCatching { GroundTruthRecordingOrigin.valueOf(recordingOrigin) }.getOrNull() ?: return null
        if (sessionId.isBlank() || reviewId.isBlank() || reviewerId.isBlank() || reviewedAtUtc.isBlank() || evidenceProvenance.isBlank()) return null
        if (parsedOutcome == CrashGroundTruthOutcome.CRASH_CONFIRMED && origin != GroundTruthRecordingOrigin.REAL) return null
        return CrashGroundTruth(parsedOutcome, GroundTruthReviewStatus.REVIEWED, crashOnsetTimestampNanos,
            sessionId, reviewId, reviewerId, reviewedAtUtc, evidenceProvenance, origin, notes)
    }
}

data class SensorRecordingQuality(
    val duplicateTimestampEvents: Long,
    val epochTimestampRegressions: Long,
    val perSensorGapCounts: Map<RawSensorType, Long>,
    val warnings: List<String>
)

/** Created only by [SensorRecordingCsvReader] after strict row and ordering validation. */
class ValidatedSensorRecording internal constructor(
    val sessionId: String,
    val events: List<RawSensorEvent>,
    val provenance: RecordingProvenance,
    val quality: SensorRecordingQuality
)

data class SensorRecordingInputIssue(val row: Int, val message: String)

sealed interface SensorRecordingReadResult {
    data class Valid(val recording: ValidatedSensorRecording) : SensorRecordingReadResult
    data class Invalid(val issues: List<SensorRecordingInputIssue>) : SensorRecordingReadResult
}

/** Reader for the stable CSV emitted by [com.manish.vahakvigil.domain.export.SensorCsvExporter]. */
object SensorRecordingCsvReader {
    private val requiredHeader = listOf(
        "session_id", "sensor_type", "timestamp_nanos", "timestamp_epoch_millis",
        "x", "y", "z", "accuracy"
    )

    fun read(csv: String, provenance: RecordingProvenance): SensorRecordingReadResult {
        val parsedRows = parseRecords(csv)
        if (parsedRows.issue != null) return SensorRecordingReadResult.Invalid(listOf(parsedRows.issue))
        val rows = parsedRows.records
        if (rows.isEmpty()) return invalid(1, "CSV is empty")
        val header = rows.first().fields.map { it.trim().lowercase(Locale.ROOT) }
        if (header.size < requiredHeader.size || header.take(requiredHeader.size) != requiredHeader) {
            return invalid(rows.first().line, "CSV header does not match the exported sensor schema")
        }
        if (rows.size == 1) return invalid(rows.first().line + 1, "CSV contains no sensor events")

        val events = ArrayList<RawSensorEvent>(rows.size - 1)
        val issues = ArrayList<SensorRecordingInputIssue>()
        var sessionId: String? = null
        var previousTimestamp = Long.MIN_VALUE
        for (row in rows.drop(1)) {
            if (row.fields.size < requiredHeader.size) {
                issues += SensorRecordingInputIssue(row.line, "Expected at least ${requiredHeader.size} columns")
                continue
            }
            val fields = row.fields
            val rowSession = fields[0]
            if (rowSession.isBlank()) {
                issues += SensorRecordingInputIssue(row.line, "Session identifier is blank")
                continue
            }
            if (sessionId == null) sessionId = rowSession
            if (sessionId != rowSession) {
                issues += SensorRecordingInputIssue(row.line, "Mixed session identifiers in one recording")
                continue
            }
            val type = runCatching { RawSensorType.valueOf(fields[1].trim().uppercase(Locale.ROOT)) }.getOrNull()
            val timestampNanos = fields[2].toLongOrNull()
            val timestampEpochMillis = fields[3].toLongOrNull()
            val x = fields[4].toFloatOrNull()
            val y = fields[5].toFloatOrNull()
            val z = fields[6].toFloatOrNull()
            val accuracy = fields[7].toIntOrNull()
            val malformed = when {
                type == null -> "Unknown sensor type"
                timestampNanos == null || timestampNanos < 0L -> "Invalid monotonic timestamp"
                timestampEpochMillis == null || timestampEpochMillis < 0L -> "Invalid epoch timestamp"
                timestampNanos < previousTimestamp -> "Sensor event ordering is non-monotonic"
                x == null || !x.isFinite() || y == null || !y.isFinite() || z == null || !z.isFinite() -> "Sensor axes must be finite numbers"
                accuracy == null || accuracy !in -1..3 -> "Sensor accuracy is outside the Android status range"
                else -> null
            }
            if (malformed != null) {
                issues += SensorRecordingInputIssue(row.line, malformed)
                continue
            }
            previousTimestamp = timestampNanos!!
            events += RawSensorEvent(
                type = type!!,
                timestampNanos = timestampNanos,
                timestampEpochMillis = timestampEpochMillis!!,
                x = x!!,
                y = y!!,
                z = z!!,
                accuracy = accuracy!!
            )
        }
        if (issues.isNotEmpty()) return SensorRecordingReadResult.Invalid(issues)
        if (events.isEmpty() || sessionId == null) return invalid(2, "CSV contains no valid sensor events")
        return SensorRecordingReadResult.Valid(
            ValidatedSensorRecording(sessionId!!, events, provenance, analyzeQuality(events))
        )
    }

    private fun analyzeQuality(events: List<RawSensorEvent>): SensorRecordingQuality {
        var duplicates = 0L
        var epochRegressions = 0L
        val priorHardwareByType = HashMap<RawSensorType, Long>()
        var priorEpoch: Long? = null
        val byType = RawSensorType.entries.associateWith { ArrayList<Long>() }
        for (event in events) {
            if (priorHardwareByType.put(event.type, event.timestampNanos) == event.timestampNanos) duplicates++
            if (priorEpoch != null && event.timestampEpochMillis < priorEpoch!!) epochRegressions++
            priorEpoch = event.timestampEpochMillis
            byType.getValue(event.type).add(event.timestampNanos)
        }
        val gaps = byType.mapValues { (_, timestamps) ->
            val intervals = timestamps.zipWithNext { first, second -> second - first }.filter { it > 0L }.sorted()
            if (intervals.isEmpty()) 0L else {
                val median = if (intervals.size % 2 == 0) {
                    (intervals[intervals.size / 2 - 1] / 2.0) + (intervals[intervals.size / 2] / 2.0)
                } else intervals[intervals.size / 2].toDouble()
                intervals.count { it > median * com.manish.vahakvigil.domain.analysis.RideSensorAnalysisLoader.IRREGULAR_GAP_MEDIAN_MULTIPLIER }.toLong()
            }
        }
        val warnings = buildList {
            if (events.none { it.type == RawSensorType.ACCELEROMETER }) add("Accelerometer stream is missing")
            if (events.none { it.type == RawSensorType.GYROSCOPE }) add("Gyroscope stream is missing")
            if (duplicates > 0) add("$duplicates duplicate hardware timestamp(s) were retained in source order")
            if (epochRegressions > 0) add("$epochRegressions epoch timestamp regression(s) found; monotonic hardware time is used for replay")
            val unreliableAccuracyCount = events.count { it.accuracy <= 0 }
            if (unreliableAccuracyCount > 0) add("$unreliableAccuracyCount sample(s) have unreliable or unavailable sensor accuracy")
            gaps.filterValues { it > 0 }.forEach { (type, count) ->
                add("$count $type interval(s) exceed ${com.manish.vahakvigil.domain.analysis.RideSensorAnalysisLoader.IRREGULAR_GAP_MEDIAN_MULTIPLIER}x that stream's median interval")
            }
        }
        return SensorRecordingQuality(duplicates, epochRegressions, gaps, warnings)
    }

    private data class CsvRecord(val fields: List<String>, val line: Int)
    private data class ParsedRecords(val records: List<CsvRecord>, val issue: SensorRecordingInputIssue?)

    /** RFC-4180 style field quoting, including escaped quotes and embedded newlines. */
    private fun parseRecords(csv: String): ParsedRecords {
        val records = ArrayList<CsvRecord>()
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var line = 1
        var recordStart = 1
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            if (quoted) {
                when {
                    char == '"' && index + 1 < csv.length && csv[index + 1] == '"' -> {
                        field.append('"'); index++
                    }
                    char == '"' -> { quoted = false; afterQuote = true }
                    else -> { field.append(char); if (char == '\n') line++ }
                }
            } else {
                when {
                    char == ',' -> { fields += field.toString(); field.setLength(0); afterQuote = false }
                    char == '\n' || char == '\r' -> {
                        fields += field.toString(); field.setLength(0)
                        if (fields.any(String::isNotEmpty)) records += CsvRecord(fields.toList(), recordStart)
                        fields.clear(); afterQuote = false
                        if (char == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') index++
                        line++; recordStart = line
                    }
                    char == '"' && field.isEmpty() && !afterQuote -> quoted = true
                    afterQuote -> return ParsedRecords(emptyList(), SensorRecordingInputIssue(line, "Unexpected character after a quoted field"))
                    char == '"' -> return ParsedRecords(emptyList(), SensorRecordingInputIssue(line, "Quote inside an unquoted field"))
                    else -> field.append(char)
                }
            }
            index++
        }
        if (quoted) return ParsedRecords(emptyList(), SensorRecordingInputIssue(recordStart, "Unterminated quoted field"))
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields += field.toString()
            if (fields.any(String::isNotEmpty)) records += CsvRecord(fields.toList(), recordStart)
        }
        return ParsedRecords(records, null)
    }

    private fun invalid(row: Int, reason: String) = SensorRecordingReadResult.Invalid(listOf(SensorRecordingInputIssue(row, reason)))
}
