package com.manish.vahakvigil.domain.evaluation

import com.manish.vahakvigil.domain.model.CrashDetectionState
import com.manish.vahakvigil.domain.model.DetectionEvaluationObservation
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.SensorReading

enum class EvaluationOutcome { NO_CANDIDATE, POSSIBLE_IMPACT_PATTERN, QUALIFIED_INCIDENT, EMERGENCY_CANDIDATE }

data class ConfidenceSummary(val sampleCount: Int, val minimum: Float?, val mean: Double?, val maximum: Float?)

data class EvidenceSummary(
    val thresholdMotionEvents: Int,
    val possibleImpactPatterns: Int,
    val qualifiedIncidentObservations: Int,
    val postImpactStatuses: Map<PostImpactStatus, Int>,
    val emergencyCandidateObservations: Int
)

/** Candidate-vs-ground-truth counts; this does not claim that a candidate confirms a real crash. */
data class SessionConfusionCounts(
    val truePositive: Int,
    val trueNegative: Int,
    val falsePositive: Int,
    val falseNegative: Int
)

sealed interface EvaluationMetricsResult {
    data class Available(val reviewedObservedSessions: Int, val counts: SessionConfusionCounts) : EvaluationMetricsResult
    data class Unavailable(val reason: String) : EvaluationMetricsResult
}

data class CrashDetectionEvaluationReport(
    val sessionId: String,
    val provenance: RecordingProvenance,
    val rawEventCount: Int,
    val sensorReadingsProcessed: Int,
    val quality: SensorRecordingQuality,
    val groundTruth: CrashGroundTruth?,
    val outcome: EvaluationOutcome,
    val finalCountdownState: EmergencyCountdownState,
    val detectionLatencyMillis: Long?,
    val confidenceScores: List<Float>,
    val confidenceSummary: ConfidenceSummary,
    val evidenceSummary: EvidenceSummary,
    val observations: List<DetectionEvaluationObservation>,
    val metricsEligibility: String,
    /** Null unless this report has reviewed, explicit binary ground truth and observed provenance. */
    val sessionMetrics: SessionConfusionCounts?
) {
    /** Stable interchange format consumed by ml_dataset evaluate. */
    fun toDatasetEvaluationJson(): String = buildString {
        append("{\"schema_version\":1,\"session_id\":").append(jsonString(sessionId))
        append(",\"provenance\":").append(jsonString(provenance.name))
        append(",\"outcome\":").append(jsonString(outcome.name))
        append(",\"final_countdown_state\":").append(jsonString(finalCountdownState.name))
        append(",\"sensor_readings_processed\":").append(sensorReadingsProcessed)
        append(",\"detection_latency_millis\":").append(detectionLatencyMillis ?: "null")
        append(",\"confidence_summary\":{\"sample_count\":").append(confidenceSummary.sampleCount)
        append(",\"minimum\":").append(confidenceSummary.minimum?.takeIf { it.isFinite() } ?: "null")
        append(",\"mean\":").append(confidenceSummary.mean?.takeIf { it.isFinite() } ?: "null")
        append(",\"maximum\":").append(confidenceSummary.maximum?.takeIf { it.isFinite() } ?: "null").append('}')
        append(",\"evidence_summary\":{\"threshold_motion_events\":").append(evidenceSummary.thresholdMotionEvents)
        append(",\"possible_impact_patterns\":").append(evidenceSummary.possibleImpactPatterns)
        append(",\"qualified_incident_observations\":").append(evidenceSummary.qualifiedIncidentObservations)
        append(",\"emergency_candidate_observations\":").append(evidenceSummary.emergencyCandidateObservations).append('}')
        append(",\"ground_truth\":")
        val truth = groundTruth
        if (truth == null) append("null") else {
            append("{\"session_id\":").append(truth.sessionId?.let(::jsonString) ?: "null")
            append(",\"outcome\":").append(truth.outcome?.name?.let(::jsonString) ?: "null")
            append(",\"review_status\":").append(jsonString(truth.reviewStatus.name))
            append(",\"review_id\":").append(truth.reviewId?.let(::jsonString) ?: "null")
            append(",\"reviewer_id\":").append(truth.reviewerId?.let(::jsonString) ?: "null")
            append(",\"reviewed_at_utc\":").append(truth.reviewedAtUtc?.let(::jsonString) ?: "null")
            append(",\"evidence_provenance\":").append(truth.evidenceProvenance?.let(::jsonString) ?: "null")
            append(",\"recording_origin\":").append(jsonString(truth.recordingOrigin.name))
            append(",\"crash_onset_timestamp_nanos\":").append(truth.crashOnsetTimestampNanos ?: "null")
            append('}')
        }
        append('}')
    }
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char -> when (char) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
        else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
    } }
    append('"')
}

sealed interface CrashDetectionEvaluationResult {
    data class Complete(val report: CrashDetectionEvaluationReport) : CrashDetectionEvaluationResult
    data class InvalidInput(val issues: List<SensorRecordingInputIssue>) : CrashDetectionEvaluationResult
}

/** Converts saved raw rows into the same synchronized snapshots emitted by AndroidSensorDataSource. */
object SensorReadingReplayAdapter {
    fun reconstruct(events: List<RawSensorEvent>): List<SensorReading> {
        val hasAccelerometer = events.any { it.type == RawSensorType.ACCELEROMETER }
        var ax = 0f; var ay = 0f; var az = 0f
        var gx = 0f; var gy = 0f; var gz = 0f
        val readings = ArrayList<SensorReading>(events.count { it.type == RawSensorType.ACCELEROMETER }.coerceAtLeast(1))
        for (event in events) {
            when (event.type) {
                RawSensorType.ACCELEROMETER -> {
                    ax = event.x; ay = event.y; az = event.z
                    readings += reading(event, ax, ay, az, gx, gy, gz)
                }
                RawSensorType.GYROSCOPE -> {
                    gx = event.x; gy = event.y; gz = event.z
                    if (!hasAccelerometer) readings += reading(event, ax, ay, az, gx, gy, gz)
                }
            }
        }
        return readings
    }

    private fun reading(event: RawSensorEvent, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) =
        SensorReading(
            timestamp = event.timestampNanos / NANOS_PER_MILLI,
            ax = ax, ay = ay, az = az,
            gx = gx, gy = gy, gz = gz
        )

    private const val NANOS_PER_MILLI = 1_000_000L
}

/** Computes binary metrics only for observed sessions with explicit reviewed crash ground truth. */
object CrashDetectionEvaluationMetrics {
    fun calculate(reports: List<CrashDetectionEvaluationReport>): EvaluationMetricsResult {
        val eligible = reports.filter { report ->
            report.provenance == RecordingProvenance.OBSERVED &&
                report.groundTruth?.reviewStatus == GroundTruthReviewStatus.REVIEWED &&
                report.groundTruth.outcome in setOf(CrashGroundTruthOutcome.CRASH_CONFIRMED, CrashGroundTruthOutcome.NO_CRASH_CONFIRMED) &&
                report.groundTruth.sessionId == report.sessionId &&
                !report.groundTruth.reviewId.isNullOrBlank() &&
                !report.groundTruth.reviewerId.isNullOrBlank() &&
                !report.groundTruth.reviewedAtUtc.isNullOrBlank() &&
                !report.groundTruth.evidenceProvenance.isNullOrBlank() &&
                report.groundTruth.recordingOrigin == GroundTruthRecordingOrigin.REAL
        }
        if (eligible.isEmpty()) {
            val reason = when {
                reports.isEmpty() -> "No evaluated sessions"
                reports.none { it.provenance == RecordingProvenance.OBSERVED } -> "Synthetic fixtures are excluded from crash metrics"
                reports.none { it.groundTruth?.reviewStatus == GroundTruthReviewStatus.REVIEWED } -> "No observed session has reviewed ground truth"
                reports.any { it.groundTruth?.outcome == CrashGroundTruthOutcome.INDETERMINATE } -> "Indeterminate outcomes are excluded from binary metrics"
                else -> "Reviewed binary outcome is missing required review provenance or real recording origin"
            }
            return EvaluationMetricsResult.Unavailable(reason)
        }
        val outcomes = eligible.map { report ->
            report.groundTruth!!.outcome!! to (report.outcome == EvaluationOutcome.EMERGENCY_CANDIDATE)
        }
        return EvaluationMetricsResult.Available(
            reviewedObservedSessions = eligible.size,
            counts = calculateConfusionCounts(outcomes)
        )
    }

    /** Pure confusion-matrix calculation; callers must apply the reviewed/observed eligibility gate first. */
    fun calculateConfusionCounts(outcomes: List<Pair<CrashGroundTruthOutcome, Boolean>>): SessionConfusionCounts {
        var tp = 0; var tn = 0; var fp = 0; var fn = 0
        outcomes.forEach { (expected, detected) ->
            when {
                expected == CrashGroundTruthOutcome.CRASH_CONFIRMED && detected -> tp++
                expected == CrashGroundTruthOutcome.NO_CRASH_CONFIRMED && !detected -> tn++
                expected == CrashGroundTruthOutcome.NO_CRASH_CONFIRMED && detected -> fp++
                expected == CrashGroundTruthOutcome.CRASH_CONFIRMED && !detected -> fn++
            }
        }
        return SessionConfusionCounts(tp, tn, fp, fn)
    }
}

internal fun CrashDetectionState.evaluationOutcome(observations: List<DetectionEvaluationObservation>): EvaluationOutcome = when {
    latestEmergencyDecision?.state == EmergencyDecisionState.EMERGENCY_CANDIDATE ||
        observations.any { it.decision.state == EmergencyDecisionState.EMERGENCY_CANDIDATE } -> EvaluationOutcome.EMERGENCY_CANDIDATE
    latestQualifiedIncident != null || observations.any { it.qualifiedIncident != null } -> EvaluationOutcome.QUALIFIED_INCIDENT
    observations.any { it.candidateEvent != null } -> EvaluationOutcome.POSSIBLE_IMPACT_PATTERN
    else -> EvaluationOutcome.NO_CANDIDATE
}
