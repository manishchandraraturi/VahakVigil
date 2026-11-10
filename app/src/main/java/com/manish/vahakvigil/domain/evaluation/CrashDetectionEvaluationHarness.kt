package com.manish.vahakvigil.domain.evaluation

import com.manish.vahakvigil.domain.model.CrashDetectionExecutionMode
import com.manish.vahakvigil.domain.model.DetectionEvaluationObservation
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline
import com.manish.vahakvigil.domain.repository.SensorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield

/** Offline, deterministic replay through [CrashDetectionPipeline]'s real production analysis path. */
class CrashDetectionEvaluationHarness {
    suspend fun replayCsv(
        csv: String,
        provenance: RecordingProvenance,
        annotation: ReviewedBinaryOutcomeAnnotation,
        onProgress: suspend (processedReadings: Int, totalReadings: Int) -> Unit = { _, _ -> }
    ): CrashDetectionEvaluationResult {
        val truth = annotation.toCrashGroundTruth()
            ?: return CrashDetectionEvaluationResult.InvalidInput(listOf(SensorRecordingInputIssue(0, "Invalid reviewed binary annotation metadata")))
        val parsed = SensorRecordingCsvReader.read(csv, provenance)
        if (parsed is SensorRecordingReadResult.Valid && parsed.recording.sessionId != annotation.sessionId) {
            return CrashDetectionEvaluationResult.InvalidInput(listOf(SensorRecordingInputIssue(0, "Annotation session ID does not match recording session ID")))
        }
        return replayCsv(csv, provenance, truth, onProgress)
    }

    suspend fun replayCsv(
        csv: String,
        provenance: RecordingProvenance,
        groundTruth: CrashGroundTruth? = null,
        onProgress: suspend (processedReadings: Int, totalReadings: Int) -> Unit = { _, _ -> }
    ): CrashDetectionEvaluationResult = when (val read = SensorRecordingCsvReader.read(csv, provenance)) {
        is SensorRecordingReadResult.Invalid -> CrashDetectionEvaluationResult.InvalidInput(read.issues)
        is SensorRecordingReadResult.Valid -> replay(read.recording, groundTruth, onProgress)
    }

    suspend fun replay(
        recording: ValidatedSensorRecording,
        groundTruth: CrashGroundTruth? = null,
        onProgress: suspend (processedReadings: Int, totalReadings: Int) -> Unit = { _, _ -> }
    ): CrashDetectionEvaluationResult {
        currentCoroutineContext().ensureActive()
        val readings = SensorReadingReplayAdapter.reconstruct(recording.events)
        val observations = ArrayList<DetectionEvaluationObservation>(readings.size)
        val source = ReplaySensorRepository(recording.events)
        var replayClockMillis = readings.firstOrNull()?.timestamp ?: 0L
        val pipeline = CrashDetectionPipeline(
            sensorRepository = source,
            timeProvider = { replayClockMillis },
            executionMode = CrashDetectionExecutionMode.EVALUATION,
            evaluationObserver = observations::add
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        pipeline.start(scope)
        var processed = 0
        try {
            for (reading in readings) {
                currentCoroutineContext().ensureActive()
                replayClockMillis = reading.timestamp
                source.emit(reading)
                processed++
                // No timer is advanced: each recorded monotonic timestamp drives one analysis step.
                while (observations.size < processed) {
                    currentCoroutineContext().ensureActive()
                    yield()
                }
                onProgress(processed, readings.size)
            }
            currentCoroutineContext().ensureActive()
            val finalState = pipeline.state.value
            val outcome = finalState.evaluationOutcome(observations)
            val latency = detectionLatencyMillis(recording, groundTruth, outcome, observations)
            val scores = observations.map { it.confidenceScore.value }
            val uniqueQualifiedIncidents = observations.mapNotNull { it.qualifiedIncident }
                .distinctBy { it.windowStartMs to it.windowEndMs }
            val preliminary = CrashDetectionEvaluationReport(
                sessionId = recording.sessionId,
                provenance = recording.provenance,
                rawEventCount = recording.events.size,
                sensorReadingsProcessed = processed,
                quality = recording.quality,
                groundTruth = groundTruth,
                outcome = outcome,
                finalCountdownState = finalState.emergencyCountdownState,
                detectionLatencyMillis = latency,
                confidenceScores = scores,
                confidenceSummary = ConfidenceSummary(
                    sampleCount = scores.size,
                    minimum = scores.minOrNull(),
                    mean = scores.takeIf { it.isNotEmpty() }?.average(),
                    maximum = scores.maxOrNull()
                ),
                evidenceSummary = EvidenceSummary(
                    thresholdMotionEvents = observations.count { it.motionEvent != null },
                    possibleImpactPatterns = observations.count { it.temporalEvent.eventType == TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE },
                    qualifiedIncidentObservations = uniqueQualifiedIncidents.size,
                    postImpactStatuses = PostImpactStatus.entries.associateWith { status ->
                        observations.count { it.postImpactResult?.status == status }
                    },
                    emergencyCandidateObservations = observations.count { it.decision.state == EmergencyDecisionState.EMERGENCY_CANDIDATE }
                ),
                observations = observations.toList(),
                metricsEligibility = "",
                sessionMetrics = null
            )
            val metrics = CrashDetectionEvaluationMetrics.calculate(listOf(preliminary))
            val completed = when (metrics) {
                is EvaluationMetricsResult.Available -> preliminary.copy(
                    metricsEligibility = "Eligible: explicit reviewed binary ground truth on an observed recording",
                    sessionMetrics = metrics.counts
                )
                is EvaluationMetricsResult.Unavailable -> preliminary.copy(metricsEligibility = metrics.reason)
            }
            return CrashDetectionEvaluationResult.Complete(completed)
        } finally {
            pipeline.stop()
            scope.cancel()
        }
    }

    private fun detectionLatencyMillis(
        recording: ValidatedSensorRecording,
        groundTruth: CrashGroundTruth?,
        outcome: EvaluationOutcome,
        observations: List<DetectionEvaluationObservation>
    ): Long? {
        if (recording.provenance != RecordingProvenance.OBSERVED ||
            groundTruth?.reviewStatus != GroundTruthReviewStatus.REVIEWED ||
            groundTruth.outcome != CrashGroundTruthOutcome.CRASH_CONFIRMED
        ) return null
        val onsetNanos = groundTruth.crashOnsetTimestampNanos ?: return null
        if (outcome != EvaluationOutcome.EMERGENCY_CANDIDATE) return null
        val detectionMillis = observations.firstOrNull {
            it.decision.state == EmergencyDecisionState.EMERGENCY_CANDIDATE
        }?.timestampMillis ?: return null
        val latencyNanos = detectionMillis * NANOS_PER_MILLI - onsetNanos
        return latencyNanos.takeIf { it >= 0L }?.div(NANOS_PER_MILLI)
    }

    private class ReplaySensorRepository(events: List<RawSensorEvent>) : SensorRepository {
        private val readings = MutableSharedFlow<SensorReading>(extraBufferCapacity = 0)
        private val _isMonitoring = MutableStateFlow(false)
        override val capabilities = SensorCapabilities(
            hasAccelerometer = events.any { it.type == RawSensorType.ACCELEROMETER },
            hasGyroscope = events.any { it.type == RawSensorType.GYROSCOPE }
        )
        override val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
        override val sensorReadings: Flow<SensorReading> = readings

        override fun startMonitoring() { _isMonitoring.value = true }
        override fun stopMonitoring() { _isMonitoring.value = false }

        suspend fun emit(reading: SensorReading) {
            check(_isMonitoring.value) { "Replay pipeline is not collecting sensor readings" }
            readings.emit(reading)
        }
    }

    private companion object { const val NANOS_PER_MILLI = 1_000_000L }
}
