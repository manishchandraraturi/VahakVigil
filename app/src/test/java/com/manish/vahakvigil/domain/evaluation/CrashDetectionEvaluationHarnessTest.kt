package com.manish.vahakvigil.domain.evaluation

import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectionEvaluationHarnessTest {
    @Test
    fun `synthetic fixture replay is deterministic and never starts countdown`() = runTest {
        val csv = syntheticDetectionCsv()
        val harness = CrashDetectionEvaluationHarness()

        val first = harness.replayCsv(csv, RecordingProvenance.SYNTHETIC_TEST_FIXTURE) as CrashDetectionEvaluationResult.Complete
        val second = harness.replayCsv(csv, RecordingProvenance.SYNTHETIC_TEST_FIXTURE) as CrashDetectionEvaluationResult.Complete

        assertEquals(first.report.outcome, second.report.outcome)
        assertEquals(first.report.confidenceScores, second.report.confidenceScores)
        assertEquals(first.report.observations, second.report.observations)
        assertEquals(EmergencyCountdownState.IDLE, first.report.finalCountdownState)
        assertTrue(first.report.evidenceSummary.possibleImpactPatterns > 0)
        assertTrue(first.report.evidenceSummary.qualifiedIncidentObservations > 0)
        assertEquals(EvaluationOutcome.EMERGENCY_CANDIDATE, first.report.outcome)
        assertEquals(first.report.sensorReadingsProcessed, first.report.confidenceSummary.sampleCount)
        assertTrue(first.report.metricsEligibility.contains("Synthetic fixtures"))
    }

    @Test
    fun `reconstruction preserves source event order timestamps and sensor snapshots`() {
        val events = listOf(
            event(RawSensorType.ACCELEROMETER, 1_000_000_000L, 0f, 0f, 9.8f),
            event(RawSensorType.GYROSCOPE, 1_100_000_000L, 0f, 0f, 4f),
            event(RawSensorType.ACCELEROMETER, 1_200_000_000L, 30f, 0f, 0f)
        )

        val readings = SensorReadingReplayAdapter.reconstruct(events)

        assertEquals(2, readings.size)
        assertEquals(1_000L, readings[0].timestamp)
        assertEquals(1_200L, readings[1].timestamp)
        assertEquals(4f, readings[1].gz)
        assertEquals(30f, readings[1].ax)
        assertEquals(listOf(1_000_000_000L, 1_100_000_000L, 1_200_000_000L), events.map { it.timestampNanos })
    }

    @Test
    fun `missing intervals are reported without reordering or interpolating events`() {
        val csv = buildString {
            appendLine("session_id,sensor_type,timestamp_nanos,timestamp_epoch_millis,x,y,z,accuracy")
            appendLine("gap,ACCELEROMETER,0,0,0,0,9.8,3")
            appendLine("gap,GYROSCOPE,0,0,0,0,0,3")
            appendLine("gap,ACCELEROMETER,10000000,10,0,0,9.8,3")
            appendLine("gap,ACCELEROMETER,20000000,20,0,0,9.8,3")
            appendLine("gap,ACCELEROMETER,120000000,120,0,0,9.8,3")
        }

        val result = SensorRecordingCsvReader.read(csv, RecordingProvenance.SYNTHETIC_TEST_FIXTURE) as SensorRecordingReadResult.Valid

        assertEquals(1L, result.recording.quality.perSensorGapCounts.getValue(RawSensorType.ACCELEROMETER))
        assertEquals(listOf(0L, 10_000_000L, 20_000_000L, 120_000_000L), result.recording.events
            .filter { it.type == RawSensorType.ACCELEROMETER }.map { it.timestampNanos })
        assertTrue(result.recording.quality.warnings.any { it.contains("interval") })
    }

    @Test
    fun `malformed rows are rejected with their source line`() = runTest {
        val result = CrashDetectionEvaluationHarness().replayCsv(
            "session_id,sensor_type,timestamp_nanos,timestamp_epoch_millis,x,y,z,accuracy\n" +
                "bad,ACCELEROMETER,not-a-time,1,0,0,9.8,3\n",
            RecordingProvenance.SYNTHETIC_TEST_FIXTURE
        )

        assertTrue(result is CrashDetectionEvaluationResult.InvalidInput)
        assertEquals(2, (result as CrashDetectionEvaluationResult.InvalidInput).issues.first().row)
    }

    @Test
    fun `replay propagates cancellation during sample processing`() = runTest {
        var observedProgress = 0
        val failure = runCatching {
            CrashDetectionEvaluationHarness().replayCsv(
                syntheticDetectionCsv(),
                RecordingProvenance.SYNTHETIC_TEST_FIXTURE,
                onProgress = { processed, _ ->
                    observedProgress = processed
                    if (processed == 2) throw CancellationException("test cancellation")
                }
            )
        }.exceptionOrNull()

        assertEquals(2, observedProgress)
        assertTrue(failure is CancellationException)
    }

    @Test
    fun `reviewed explicit observed outcomes produce confusion counts`() {
        // Synthetic DTO-only fixtures exercise eligibility and arithmetic; they are not real performance data.
        val syntheticReportFixtures = listOf(
            metricFixture("tp", CrashGroundTruthOutcome.CRASH_CONFIRMED, EvaluationOutcome.EMERGENCY_CANDIDATE),
            metricFixture("tn", CrashGroundTruthOutcome.NO_CRASH_CONFIRMED, EvaluationOutcome.NO_CANDIDATE),
            metricFixture("fp", CrashGroundTruthOutcome.NO_CRASH_CONFIRMED, EvaluationOutcome.EMERGENCY_CANDIDATE),
            metricFixture("fn", CrashGroundTruthOutcome.CRASH_CONFIRMED, EvaluationOutcome.NO_CANDIDATE)
        )

        val result = CrashDetectionEvaluationMetrics.calculate(syntheticReportFixtures) as EvaluationMetricsResult.Available

        assertEquals(4, result.reviewedObservedSessions)
        assertEquals(SessionConfusionCounts(1, 1, 1, 1), result.counts)
    }

    @Test
    fun `unreviewed and synthetic recordings are refused for crash metrics`() = runTest {
        val synthetic = CrashDetectionEvaluationHarness().replayCsv(
            syntheticDetectionCsv(),
            RecordingProvenance.SYNTHETIC_TEST_FIXTURE,
            CrashGroundTruth(CrashGroundTruthOutcome.CRASH_CONFIRMED, GroundTruthReviewStatus.REVIEWED)
        ) as CrashDetectionEvaluationResult.Complete
        assertTrue(synthetic.report.metricsEligibility.contains("Synthetic fixtures"))

        val unreviewed = CrashDetectionEvaluationHarness().replayCsv(
            simpleCsv(),
            RecordingProvenance.OBSERVED,
            CrashGroundTruth(CrashGroundTruthOutcome.NO_CRASH_CONFIRMED, GroundTruthReviewStatus.UNREVIEWED)
        ) as CrashDetectionEvaluationResult.Complete
        assertTrue(unreviewed.report.metricsEligibility.contains("reviewed ground truth"))
        assertFalse(unreviewed.report.detectionLatencyMillis != null)
        assertEquals(null, unreviewed.report.sessionMetrics)
    }

    @Test
    fun `reviewed descriptive labels without binary outcome do not enable metrics`() = runTest {
        val result = CrashDetectionEvaluationHarness().replayCsv(
            simpleCsv(), RecordingProvenance.OBSERVED,
            CrashGroundTruth(outcome = null, reviewStatus = GroundTruthReviewStatus.REVIEWED)
        ) as CrashDetectionEvaluationResult.Complete

        assertTrue(result.report.metricsEligibility.contains("review provenance"))
    }

    @Test
    fun `evaluation JSON includes session and reviewed annotation provenance`() = runTest {
        val truth = CrashGroundTruth(
            CrashGroundTruthOutcome.CRASH_CONFIRMED, GroundTruthReviewStatus.REVIEWED,
            sessionId = "observed-id", reviewId = "review-1", reviewerId = "reviewer-pseudo",
            reviewedAtUtc = "2025-01-01T00:00:00Z", evidenceProvenance = "reviewed incident report",
            recordingOrigin = GroundTruthRecordingOrigin.REAL
        )
        val report = (CrashDetectionEvaluationHarness().replayCsv(simpleCsv(), RecordingProvenance.OBSERVED, truth)
            as CrashDetectionEvaluationResult.Complete).report

        val json = report.toDatasetEvaluationJson()
        assertTrue(json.contains("\"session_id\":\"observed-id\""))
        assertTrue(json.contains("\"outcome\":\"CRASH_CONFIRMED\""))
        assertTrue(json.contains("\"review_id\":\"review-1\""))
        assertTrue(json.contains("\"final_countdown_state\":\"IDLE\""))
    }

    @Test
    fun `reviewed annotation is associated with matching replay session`() = runTest {
        val annotation = ReviewedBinaryOutcomeAnnotation(
            "observed-id", "review-1", "NO_CRASH_CONFIRMED", "reviewer", "2025-01-01T00:00:00Z",
            "reviewed incident report", "REAL"
        )
        val matching = CrashDetectionEvaluationHarness().replayCsv(simpleCsv(), RecordingProvenance.OBSERVED, annotation)
        assertTrue(matching is CrashDetectionEvaluationResult.Complete)
        val mismatched = CrashDetectionEvaluationHarness().replayCsv(
            simpleCsv().replace("observed-id", "other-session"), RecordingProvenance.OBSERVED, annotation
        )
        assertTrue(mismatched is CrashDetectionEvaluationResult.InvalidInput)
    }

    private fun syntheticDetectionCsv(): String = buildString {
        appendLine("session_id,sensor_type,timestamp_nanos,timestamp_epoch_millis,x,y,z,accuracy")
        fun row(type: RawSensorType, millis: Long, x: Float, y: Float, z: Float) {
            append("synthetic-fixture,").append(type.name).append(',')
                .append(millis * NANOS_PER_MILLI).append(',').append(millis).append(',')
                .append(x).append(',').append(y).append(',').append(z).appendLine(",3")
        }
        row(RawSensorType.ACCELEROMETER, 0, 30f, 0f, 0f)
        row(RawSensorType.GYROSCOPE, 100, 0f, 0f, 5f)
        for (millis in listOf(200L, 300L, 400L)) row(RawSensorType.ACCELEROMETER, millis, 0f, 0f, 9.8f)
        row(RawSensorType.GYROSCOPE, 500, 0f, 0f, 0f)
        for (millis in 600L..3_000L step 100L) row(RawSensorType.ACCELEROMETER, millis, 0f, 0f, 9.8f)
    }

    private fun simpleCsv() = "session_id,sensor_type,timestamp_nanos,timestamp_epoch_millis,x,y,z,accuracy\n" +
        "observed-id,ACCELEROMETER,1000000000,1000,0,0,9.8,3\n" +
        "observed-id,GYROSCOPE,1100000000,1100,0,0,0,3\n"

    private fun event(type: RawSensorType, timestampNanos: Long, x: Float, y: Float, z: Float) = RawSensorEvent(
        type, timestampNanos, timestampNanos / NANOS_PER_MILLI, x, y, z, 3
    )

    private fun metricFixture(
        sessionId: String,
        expected: CrashGroundTruthOutcome,
        detected: EvaluationOutcome
    ) = CrashDetectionEvaluationReport(
        sessionId = "synthetic-metric-fixture-$sessionId",
        provenance = RecordingProvenance.OBSERVED,
        rawEventCount = 0,
        sensorReadingsProcessed = 0,
        quality = SensorRecordingQuality(0, 0, RawSensorType.entries.associateWith { 0L }, emptyList()),
        groundTruth = CrashGroundTruth(
            expected, GroundTruthReviewStatus.REVIEWED, sessionId = "synthetic-metric-fixture-$sessionId",
            reviewId = "review-$sessionId", reviewerId = "synthetic-reviewer",
            reviewedAtUtc = "2025-01-01T00:00:00Z", evidenceProvenance = "synthetic DTO fixture",
            recordingOrigin = GroundTruthRecordingOrigin.REAL
        ),
        outcome = detected,
        finalCountdownState = EmergencyCountdownState.IDLE,
        detectionLatencyMillis = null,
        confidenceScores = emptyList(),
        confidenceSummary = ConfidenceSummary(0, null, null, null),
        evidenceSummary = EvidenceSummary(0, 0, 0, emptyMap(), 0),
        observations = emptyList(),
        metricsEligibility = "Synthetic test fixture only",
        sessionMetrics = null
    )

    private companion object { const val NANOS_PER_MILLI = 1_000_000L }
}

