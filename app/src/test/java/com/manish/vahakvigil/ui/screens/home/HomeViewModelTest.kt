package com.manish.vahakvigil.ui.screens.home

import android.Manifest
import com.manish.vahakvigil.data.repository.SensorRepositoryImpl
import com.manish.vahakvigil.domain.analyzer.EmergencyCountdownEngine
import com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine
import com.manish.vahakvigil.domain.analyzer.MotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionEventType
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import com.manish.vahakvigil.domain.orchestration.EmergencyActionExecutor
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import com.manish.vahakvigil.sensor.FakeSensorDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataSource: FakeSensorDataSource
    private lateinit var repository: SensorRepositoryImpl
    private lateinit var viewModel: HomeViewModel
    private lateinit var fakeActionExecutor: TestActionExecutor
    private lateinit var actionOrchestrator: EmergencyActionOrchestrator
    private lateinit var fakeMedicalRepository: FakeMedicalProfileRepository
    private lateinit var fakeContactRepository: FakeContactRepository
    private lateinit var testDispatchDataCompiler: EmergencyDispatchDataCompiler

    /**
     * Explicit low thresholds and short window for testing.
     * NOT production values — only used to exercise ViewModel wiring.
     *
     * - acceleration ≥ 10 m/s²  → HIGH_ACCELERATION
     * - gyroscope   ≥ 2 rad/s   → HIGH_GYROSCOPE
     * - window        = 500ms
     */
    private val testConfig = MotionDetectionConfig(
        accelerationThresholdMs2 = 10f,
        gyroscopeThresholdRads = 2f,
        temporalWindowMillis = 500L
    )

    private val testQualificationConfig = com.manish.vahakvigil.domain.model.EventQualificationConfig(
        minimumConfidenceScore = 0.40f,
        requiredConsecutiveWindows = 2,
        qualificationWindowMillis = 2000L
    )

    private val testPostImpactConfig = com.manish.vahakvigil.domain.model.PostImpactMotionConfig(
        postImpactWindowMillis = 5000L,
        stillnessAccelerationThreshold = 10.5f,
        stillnessGyroscopeThreshold = 0.5f,
        requiredStillnessDurationMillis = 1000L
    )

    private val testDecisionConfig = EmergencyDecisionConfig(
        countdownDurationMillis = 75_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataSource = FakeSensorDataSource(
            capabilities = SensorCapabilities(hasAccelerometer = true, hasGyroscope = true)
        )
        repository = SensorRepositoryImpl(dataSource)
        val motionAnalyzer = MotionAnalyzer(testConfig)
        val qualificationEngine = com.manish.vahakvigil.domain.analyzer.EventQualificationEngine(testQualificationConfig)
        val postImpactAnalyzer = com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer(testPostImpactConfig)
        val decisionEngine = EmergencyDecisionEngine(testDecisionConfig)
        val countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis)
        fakeActionExecutor = TestActionExecutor()
        actionOrchestrator = EmergencyActionOrchestrator(fakeActionExecutor)
        fakeMedicalRepository = FakeMedicalProfileRepository(
            MedicalProfile(
                name = "Test Rider",
                bloodGroup = "A+",
                allergies = "None",
                conditions = "None",
                medications = "None",
                notes = "None"
            )
        )
        fakeContactRepository = FakeContactRepository(
            listOf(
                Contact(
                    id = 1L,
                    name = "Emergency Contact",
                    relationship = "Family",
                    phone = "1234567890"
                )
            )
        )
        testDispatchDataCompiler = EmergencyDispatchDataCompiler(fakeMedicalRepository, fakeContactRepository)
        viewModel = HomeViewModel(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = motionAnalyzer,
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, motionAnalyzer),
            qualificationEngine = qualificationEngine,
            postImpactAnalyzer = postImpactAnalyzer,
            decisionEngine = decisionEngine,
            countdownEngine = countdownEngine,
            actionOrchestrator = actionOrchestrator,
            dispatchDataCompiler = testDispatchDataCompiler
        )
    }

    @After
    fun tearDown() {
        viewModel.pipeline.stop()
        Dispatchers.resetMain()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun normalReading(timestamp: Long) = SensorReading(
        timestamp = timestamp,
        ax = 1f, ay = 0f, az = 0f,
        gx = 0f, gy = 0f, gz = 0f,
        accelerationMagnitude = 1f,
        gyroscopeMagnitude = 0f
    )

    private fun highAccReading(timestamp: Long) = SensorReading(
        timestamp = timestamp,
        ax = 15f, ay = 0f, az = 0f,
        gx = 0f, gy = 0f, gz = 0f,
        accelerationMagnitude = 15f,  // > 10f threshold
        gyroscopeMagnitude = 0f
    )

    private fun highGyroReading(timestamp: Long) = SensorReading(
        timestamp = timestamp,
        ax = 0f, ay = 0f, az = 0f,
        gx = 4f, gy = 0f, gz = 0f,
        accelerationMagnitude = 0f,
        gyroscopeMagnitude = 4f       // > 2f threshold
    )

    // ─── Original monitoring tests (preserved) ────────────────────────────────

    @Test
    fun `initial state is not monitoring with empty latest reading`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isMonitoring)
        assertNull(state.latestReading)
        assertTrue(state.capabilities.hasAccelerometer)
        assertTrue(state.capabilities.hasGyroscope)
    }

    @Test
    fun `toggleMonitoring starts monitoring and updates state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMonitoring)
        assertTrue(repository.isMonitoring.value)
        assertTrue(dataSource.isListening)
    }

    @Test
    fun `incoming sensor readings update latestReading when monitoring`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        val reading = SensorReading(
            timestamp = 5000L,
            ax = 1.2f,
            ay = 9.8f,
            az = 0.5f,
            gx = 0.02f,
            gy = 0.01f,
            gz = 0.00f
        )
        dataSource.emitReading(reading)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertNotNull(updatedState.latestReading)
        assertEquals(1.2f, updatedState.latestReading?.ax)
        assertEquals(9.8f, updatedState.latestReading?.ay)
    }

    @Test
    fun `toggleMonitoring stops monitoring and clears latestReading`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        dataSource.emitReading(SensorReading(ax = 1f, ay = 2f, az = 3f))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.latestReading)

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        val stoppedState = viewModel.uiState.value
        assertFalse(stoppedState.isMonitoring)
        assertNull(stoppedState.latestReading)
        assertFalse(dataSource.isListening)
    }

    // ─── MotionAnalyzer wiring tests ──────────────────────────────────────────

    @Test
    fun `latestMotionEvent is null in initial state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.latestMotionEvent)
    }

    @Test
    fun `normal reading below thresholds does not produce a latestMotionEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            dataSource.emitReading(normalReading(1000L))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.latestMotionEvent)
        }

    @Test
    fun `HIGH_ACCELERATION reading produces HIGH_ACCELERATION latestMotionEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            dataSource.emitReading(highAccReading(1000L))
            advanceUntilIdle()

            val event = viewModel.uiState.value.latestMotionEvent
            assertNotNull(event)
            assertEquals(MotionEventType.HIGH_ACCELERATION, event!!.eventType)
        }

    @Test
    fun `HIGH_GYROSCOPE reading produces HIGH_GYROSCOPE latestMotionEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            dataSource.emitReading(highGyroReading(1000L))
            advanceUntilIdle()

            val event = viewModel.uiState.value.latestMotionEvent
            assertNotNull(event)
            assertEquals(MotionEventType.HIGH_GYROSCOPE, event!!.eventType)
        }

    @Test
    fun `COMBINED_IMPACT reading produces COMBINED_IMPACT latestMotionEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            val reading = SensorReading(
                timestamp = 1000L,
                ax = 20f, ay = 0f, az = 0f,
                gx = 5f, gy = 0f, gz = 0f,
                accelerationMagnitude = 20f,
                gyroscopeMagnitude = 5f
            )
            dataSource.emitReading(reading)
            advanceUntilIdle()

            val event = viewModel.uiState.value.latestMotionEvent
            assertNotNull(event)
            assertEquals(MotionEventType.COMBINED_IMPACT, event!!.eventType)
        }

    @Test
    fun `latestMotionEvent is cleared when monitoring stops`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        dataSource.emitReading(highAccReading(1000L))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.latestMotionEvent)

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull(
            "latestMotionEvent must be cleared when monitoring stops",
            viewModel.uiState.value.latestMotionEvent
        )
    }

    @Test
    fun `COMBINED_IMPACT reading sets displayedMotionEvent and releases after 1500ms to current pipeline state`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            val combinedReading = SensorReading(
                timestamp = 1000L,
                ax = 20f, ay = 0f, az = 0f,
                gx = 5f, gy = 0f, gz = 0f,
                accelerationMagnitude = 20f,
                gyroscopeMagnitude = 5f
            )
            dataSource.emitReading(combinedReading)
            testScheduler.runCurrent()

            // Critical state is held in displayedMotionEvent
            assertEquals(MotionEventType.COMBINED_IMPACT, viewModel.uiState.value.displayedMotionEvent?.eventType)

            // Subsequent normal reading arrives during hold window
            dataSource.emitReading(normalReading(1200L))
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()

            // Still held during 1500ms hold window
            assertEquals(MotionEventType.COMBINED_IMPACT, viewModel.uiState.value.displayedMotionEvent?.eventType)

            // Advance past 1500ms hold window
            testScheduler.advanceTimeBy(1100L)
            testScheduler.runCurrent()

            // Must release to CURRENT pipeline state (which is normal baseline -> null)
            assertNull("displayedMotionEvent must release to current baseline state", viewModel.uiState.value.displayedMotionEvent)
        }

    @Test
    fun `hold expiry updates to latest current motion event rather than restoring old state`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            val combinedReading = SensorReading(
                timestamp = 1000L,
                ax = 20f, ay = 0f, az = 0f,
                gx = 5f, gy = 0f, gz = 0f,
                accelerationMagnitude = 20f,
                gyroscopeMagnitude = 5f
            )
            dataSource.emitReading(combinedReading)
            testScheduler.runCurrent()

            assertEquals(MotionEventType.COMBINED_IMPACT, viewModel.uiState.value.displayedMotionEvent?.eventType)

            // Subsequent reading is HIGH_GYROSCOPE
            dataSource.emitReading(highGyroReading(2000L))
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(500L)
            testScheduler.runCurrent()

            // Still held as COMBINED_IMPACT during hold window
            assertEquals(MotionEventType.COMBINED_IMPACT, viewModel.uiState.value.displayedMotionEvent?.eventType)

            // Advance past 1500ms
            testScheduler.advanceTimeBy(1100L)
            testScheduler.runCurrent()

            // Must show the CURRENT actual state (HIGH_GYROSCOPE), NOT old baseline
            assertEquals(MotionEventType.HIGH_GYROSCOPE, viewModel.uiState.value.displayedMotionEvent?.eventType)
        }

    @Test
    fun `stopping monitoring immediately clears displayedMotionEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            val combinedReading = SensorReading(
                timestamp = 1000L,
                ax = 20f, ay = 0f, az = 0f,
                gx = 5f, gy = 0f, gz = 0f,
                accelerationMagnitude = 20f,
                gyroscopeMagnitude = 5f
            )
            dataSource.emitReading(combinedReading)
            testScheduler.runCurrent()

            assertEquals(MotionEventType.COMBINED_IMPACT, viewModel.uiState.value.displayedMotionEvent?.eventType)

            viewModel.toggleMonitoring()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.displayedMotionEvent)
        }

    // ─── TemporalMotionAnalyzer wiring tests ──────────────────────────────────

    @Test
    fun `latestTemporalEvent is null in initial state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.latestTemporalEvent)
    }

    @Test
    fun `single normal reading does not produce latestTemporalEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            dataSource.emitReading(normalReading(1000L))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.latestTemporalEvent)
        }

    @Test
    fun `single high-acceleration reading alone does not produce latestTemporalEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            dataSource.emitReading(highAccReading(1000L))
            advanceUntilIdle()

            assertNull(
                "Single acceleration spike should not produce a temporal sequence",
                viewModel.uiState.value.latestTemporalEvent
            )
        }

    @Test
    fun `high acc then high gyro within window produces POSSIBLE_IMPACT_SEQUENCE in latestTemporalEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // t=1000: high acceleration
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()

            // t=1300: high gyroscope — within 500ms window
            dataSource.emitReading(highGyroReading(timestamp = 1300L))
            advanceUntilIdle()

            val temporalEvent = viewModel.uiState.value.latestTemporalEvent
            assertNotNull("Should detect impact sequence", temporalEvent)
            assertEquals(
                TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
                temporalEvent!!.eventType
            )
        }

    @Test
    fun `high gyro before high acc does not produce latestTemporalEvent (ordering enforced)`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // gyro first → acc after — wrong order for the pattern
            dataSource.emitReading(highGyroReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highAccReading(timestamp = 1200L))
            advanceUntilIdle()

            assertNull(
                "Reversed order (gyro→acc) should not produce a sequence",
                viewModel.uiState.value.latestTemporalEvent
            )
        }

    @Test
    fun `latestTemporalEvent is cleared when monitoring stops`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Produce a sequence
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.latestTemporalEvent)

        // Stop monitoring
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull(
            "latestTemporalEvent must be cleared when monitoring stops",
            viewModel.uiState.value.latestTemporalEvent
        )
    }

    @Test
    fun `buffer is cleared on stop so fresh monitoring starts with empty window`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            // First session: produce a sequence
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1200L))
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.latestTemporalEvent)

            // Stop
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // Second session: emit only a single gyro reading — should NOT produce sequence
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 9000L))
            advanceUntilIdle()

            assertNull(
                "Buffer should be empty after restart — single gyro should not produce sequence",
                viewModel.uiState.value.latestTemporalEvent
            )
        }

    // ─── CandidateAccidentEvent wiring tests ──────────────────────────────────

    @Test
    fun `latestCandidateAccidentEvent is null in initial state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.latestCandidateAccidentEvent)
    }

    @Test
    fun `single normal reading does not produce latestCandidateAccidentEvent`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            dataSource.emitReading(normalReading(1000L))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.latestCandidateAccidentEvent)
        }

    @Test
    fun `pipeline correctly produces CandidateAccidentEvent on impact sequence`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // t=1000: High acceleration (15 m/s² > 10 threshold)
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()

            // t=1250: High gyroscope (4 rad/s > 2 threshold) — within 500ms window
            dataSource.emitReading(highGyroReading(timestamp = 1250L))
            advanceUntilIdle()

            val candidate = viewModel.uiState.value.latestCandidateAccidentEvent
            assertNotNull("CandidateAccidentEvent must be generated for impact sequence", candidate)
            assertEquals(1000L, candidate!!.windowStartMs)
            assertEquals(1250L, candidate.windowEndMs)
            assertEquals(2, candidate.readingCount)
            assertEquals(
                TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
                candidate.temporalEvent.eventType
            )
            // Pattern factor alone is 0.40; with peak acc/gyro/persistence, score > 0.40
            assertTrue(
                "Confidence score must reflect detected evidence, was ${candidate.confidenceScore.value}",
                candidate.confidenceScore.value > 0.40f
            )
        }

    @Test
    fun `stopping monitoring clears latestCandidateAccidentEvent and temporal state`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // Produce candidate event
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1250L))
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.latestCandidateAccidentEvent)
            assertNotNull(viewModel.uiState.value.latestTemporalEvent)

            // Stop monitoring
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            assertNull(
                "latestCandidateAccidentEvent must be cleared when monitoring stops",
                viewModel.uiState.value.latestCandidateAccidentEvent
            )
            assertNull(
                "latestTemporalEvent must be cleared when monitoring stops",
                viewModel.uiState.value.latestTemporalEvent
            )
        }

    // ─── QualifiedIncident wiring tests ───────────────────────────────────────

    @Test
    fun `pipeline correctly produces QualifiedIncident when required consecutive candidates occur`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // Initially null
            assertNull(viewModel.uiState.value.latestQualifiedIncident)

            // Sequence 1 (Candidate 1 produced at t=1200)
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1200L))
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.latestCandidateAccidentEvent)
            // 1 candidate is not enough (testQualificationConfig requires 2 consecutive)
            assertNull(viewModel.uiState.value.latestQualifiedIncident)

            // Sequence 2 (Candidate 2 produced at t=1600 within 2000ms window)
            dataSource.emitReading(highAccReading(timestamp = 1400L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1600L))
            advanceUntilIdle()

            val incident = viewModel.uiState.value.latestQualifiedIncident
            assertNotNull("QualifiedIncident must be produced after reaching required consecutive candidates", incident)
            assertEquals(2, incident!!.qualifyingWindowCount)
            assertTrue("Score must reflect qualifying confidence", incident.qualifyingConfidenceScore.value >= 0.40f)
        }

    @Test
    fun `stopping monitoring clears latestQualifiedIncident and resets qualification state`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // Candidate 1
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1200L))
            advanceUntilIdle()

            // Candidate 2 -> Qualifies
            dataSource.emitReading(highAccReading(timestamp = 1400L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1600L))
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.latestQualifiedIncident)

            // Stop monitoring
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            assertNull(
                "latestQualifiedIncident must be cleared when monitoring stops",
                viewModel.uiState.value.latestQualifiedIncident
            )
            assertNull(viewModel.uiState.value.latestCandidateAccidentEvent)
        }

    @Test
    fun `starting a new monitoring session does not inherit previous qualification evidence`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            // Session 1: emit 1 candidate (not qualifying yet)
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1200L))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.latestQualifiedIncident)

            // Stop session 1
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.latestQualifiedIncident)

            // Session 2: start fresh session and emit only 1 candidate
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            dataSource.emitReading(highAccReading(timestamp = 9000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 9200L))
            advanceUntilIdle()

            // Should still be null because session 1 evidence was reset and 2 consecutive are required
            assertNull(
                "New session must not inherit candidate evidence from previous session",
                viewModel.uiState.value.latestQualifiedIncident
            )
        }

    // ─── PostImpactMotionAnalyzer wiring tests ────────────────────────────────

    @Test
    fun `HomeViewModel starts post-impact analysis only after QualifiedIncident`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // Emit normal readings before any incident
            dataSource.emitReading(normalReading(1000L))
            advanceUntilIdle()

            // Post-impact analysis must NOT start before a QualifiedIncident exists
            assertNull(viewModel.uiState.value.latestPostImpactResult)
            assertNull(viewModel.uiState.value.latestQualifiedIncident)
        }

    @Test
    fun `HomeViewModel exposes post-impact motion state correctly after QualifiedIncident`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.toggleMonitoring()
            advanceUntilIdle()

            // Candidate 1 (t=1000..1200)
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1200L))
            advanceUntilIdle()

            // Candidate 2 (t=1400..1600) -> Produces QualifiedIncident with windowEndMs = 1600L
            dataSource.emitReading(highAccReading(timestamp = 1400L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1600L))
            advanceUntilIdle()

            val incident = viewModel.uiState.value.latestQualifiedIncident
            assertNotNull("QualifiedIncident must be present", incident)

            // Post-impact reading 1: still reading at t=2000L (duration 0 ms < 1000 ms required)
            dataSource.emitReading(
                SensorReading(
                    timestamp = 2000L,
                    ax = 9.8f, ay = 0f, az = 0f,
                    gx = 0.1f, gy = 0f, gz = 0f,
                    accelerationMagnitude = 9.8f,
                    gyroscopeMagnitude = 0.1f
                )
            )
            advanceUntilIdle()

            val result1 = viewModel.uiState.value.latestPostImpactResult
            assertNotNull(result1)
            assertEquals(com.manish.vahakvigil.domain.model.PostImpactStatus.OBSERVING, result1!!.status)

            // Post-impact reading 2: still reading at t=3100L (3100 - 2000 = 1100 ms >= 1000 ms required)
            dataSource.emitReading(
                SensorReading(
                    timestamp = 3100L,
                    ax = 9.8f, ay = 0f, az = 0f,
                    gx = 0.1f, gy = 0f, gz = 0f,
                    accelerationMagnitude = 9.8f,
                    gyroscopeMagnitude = 0.1f
                )
            )
            advanceUntilIdle()

            val result2 = viewModel.uiState.value.latestPostImpactResult
            assertNotNull(result2)
            assertEquals(com.manish.vahakvigil.domain.model.PostImpactStatus.STILL, result2!!.status)
            assertEquals(1100L, result2.stillnessDurationMs)
        }

    @Test
    fun `stopping monitoring clears post-impact state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        // Post-impact reading
        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.latestPostImpactResult)

        // Stop monitoring
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull(
            "latestPostImpactResult must be cleared when monitoring stops",
            viewModel.uiState.value.latestPostImpactResult
        )
    }

    @Test
    fun `starting a new session does not inherit previous post-impact readings`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            // Session 1: qualify and record post-impact readings
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            dataSource.emitReading(highAccReading(timestamp = 1000L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1200L))
            advanceUntilIdle()
            dataSource.emitReading(highAccReading(timestamp = 1400L))
            advanceUntilIdle()
            dataSource.emitReading(highGyroReading(timestamp = 1600L))
            advanceUntilIdle()
            dataSource.emitReading(
                SensorReading(
                    timestamp = 2000L,
                    ax = 9.8f, ay = 0f, az = 0f,
                    gx = 0.1f, gy = 0f, gz = 0f,
                    accelerationMagnitude = 9.8f,
                    gyroscopeMagnitude = 0.1f
                )
            )
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.latestPostImpactResult)

            // Stop Session 1
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.latestPostImpactResult)

            // Session 2: start fresh session and emit normal readings
            viewModel.toggleMonitoring()
            advanceUntilIdle()
            dataSource.emitReading(normalReading(timestamp = 9000L))
            advanceUntilIdle()

            // Session 2 must be completely fresh with no post-impact state
            assertNull(
                "New session must not inherit previous post-impact state",
                viewModel.uiState.value.latestPostImpactResult
            )
        }

    // ─── EmergencyDecision & Cancellation Countdown wiring tests ──────────────

    @Test
    fun `latestEmergencyDecision remains NO_EMERGENCY before qualification`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Normal reading
        dataSource.emitReading(normalReading(timestamp = 1000L))
        advanceUntilIdle()

        val decision = viewModel.uiState.value.latestEmergencyDecision
        assertNotNull(decision)
        assertEquals(EmergencyDecisionState.NO_EMERGENCY, decision!!.state)
        assertEquals(EmergencyCountdownState.IDLE, viewModel.uiState.value.emergencyCountdownState)
    }

    @Test
    fun `STILL after QualifiedIncident produces EMERGENCY_CANDIDATE`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.latestQualifiedIncident)

        // Post-impact readings spanning >= 1000ms
        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        val decision = viewModel.uiState.value.latestEmergencyDecision
        assertNotNull(decision)
        assertEquals(EmergencyDecisionState.EMERGENCY_CANDIDATE, decision!!.state)
    }

    @Test
    fun `countdown starts exactly once upon EMERGENCY_CANDIDATE`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        // Reach STILL at timestamp 3100L
        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.COUNTING_DOWN, viewModel.uiState.value.emergencyCountdownState)
        assertEquals(75_000L, viewModel.uiState.value.emergencyCountdownRemainingMillis)
    }

    @Test
    fun `repeated sensor readings do not restart countdown`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        // Starts countdown at 3100L
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertEquals(75_000L, viewModel.uiState.value.emergencyCountdownRemainingMillis)

        // Next reading 1000ms later at 4100L
        dataSource.emitReading(
            SensorReading(
                timestamp = 4100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.COUNTING_DOWN, viewModel.uiState.value.emergencyCountdownState)
        assertEquals(74_000L, viewModel.uiState.value.emergencyCountdownRemainingMillis)
    }

    @Test
    fun `cancelEmergencyCountdown transitions countdown to CANCELLED`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, viewModel.uiState.value.emergencyCountdownState)

        // User taps "I'm Fine"
        viewModel.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.CANCELLED, viewModel.uiState.value.emergencyCountdownState)
        assertEquals(75_000L, viewModel.uiState.value.emergencyCountdownRemainingMillis)
    }

    @Test
    fun `stopping monitoring clears emergency decision and countdown state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        // Stop monitoring
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("Emergency decision must be null when stopped", state.latestEmergencyDecision)
        assertEquals(EmergencyCountdownState.IDLE, state.emergencyCountdownState)
        assertEquals(75_000L, state.emergencyCountdownRemainingMillis)
    }

    @Test
    fun `new monitoring session starts clean`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Start session 1
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Start session 2
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        dataSource.emitReading(normalReading(timestamp = 9000L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.latestEmergencyDecision)
        assertEquals(EmergencyDecisionState.NO_EMERGENCY, state.latestEmergencyDecision!!.state)
        assertEquals(EmergencyCountdownState.IDLE, state.emergencyCountdownState)
        assertEquals(75_000L, state.emergencyCountdownRemainingMillis)
    }

    @Test
    fun `previously cancelled countdown does not leak into a new session`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Session 1: qualify, reach candidate, cancel
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        viewModel.cancelEmergencyCountdown()
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.CANCELLED, viewModel.uiState.value.emergencyCountdownState)

        // Stop session 1
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Session 2: start fresh
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        dataSource.emitReading(normalReading(timestamp = 10000L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EmergencyCountdownState.IDLE, state.emergencyCountdownState)
        assertEquals(75_000L, state.emergencyCountdownRemainingMillis)
        assertEquals(EmergencyDecisionState.NO_EMERGENCY, state.latestEmergencyDecision?.state)
    }

    // ─── Emergency Action Orchestration wiring tests ──────────────────────────

    @Test
    fun `action result remains NOT_TRIGGERED before expiry`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        dataSource.emitReading(normalReading(timestamp = 1000L))
        advanceUntilIdle()

        val actionResult = viewModel.uiState.value.latestEmergencyActionResult
        assertNotNull(actionResult)
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, actionResult!!.status)
        assertEquals(0, fakeActionExecutor.callCount)
    }

    @Test
    fun `countdown expiry produces action orchestration`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        // Stillness sequence (starts countdown at 3100L)
        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, viewModel.uiState.value.emergencyCountdownState)

        // Reading past expiry duration (3100L + 75_000L = 78_100L)
        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.EXPIRED, viewModel.uiState.value.emergencyCountdownState)
        val actionResult = viewModel.uiState.value.latestEmergencyActionResult
        assertNotNull(actionResult)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, actionResult!!.status)
        assertEquals(1, fakeActionExecutor.callCount)
    }

    @Test
    fun `cancellation produces no action`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify incident and reach STILL at 3100L
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, viewModel.uiState.value.emergencyCountdownState)

        // Cancel
        viewModel.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.CANCELLED, viewModel.uiState.value.emergencyCountdownState)
        val actionResult = viewModel.uiState.value.latestEmergencyActionResult
        assertNotNull(actionResult)
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, actionResult!!.status)
        assertEquals(0, fakeActionExecutor.callCount)
    }

    @Test
    fun `repeated sensor readings do not duplicate actions`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        // First expired reading at 78_100L
        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertEquals(1, fakeActionExecutor.callCount)

        // Subsequent expired readings at later timestamps
        dataSource.emitReading(
            SensorReading(
                timestamp = 79_000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 80_000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        // Call count MUST remain 1
        assertEquals(1, fakeActionExecutor.callCount)
    }

    @Test
    fun `stopping monitoring clears action state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        // Expire
        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.latestEmergencyActionResult)

        // Stop monitoring
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull("Action state must be null when stopped", viewModel.uiState.value.latestEmergencyActionResult)
    }

    @Test
    fun `new monitoring session starts with clean action state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Session 1: qualify, reach still, expire
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        assertEquals(EmergencyActionResultStatus.TRIGGERED, viewModel.uiState.value.latestEmergencyActionResult?.status)

        // Stop session 1
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Session 2: start clean
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        dataSource.emitReading(normalReading(timestamp = 90_000L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EmergencyCountdownState.IDLE, state.emergencyCountdownState)
        assertNotNull(state.latestEmergencyActionResult)
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, state.latestEmergencyActionResult?.status)
    }

    // ─── Emergency Dispatch Payload wiring tests ──────────────────────────────

    @Test
    fun `prepareDispatchPayload compiles payload into uiState`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val payload = viewModel.prepareDispatchPayload(incidentTimestamp = 5555L)

        assertNotNull(payload)
        assertEquals(5555L, payload?.incidentTimestamp)
        assertEquals(payload, viewModel.uiState.value.latestEmergencyDispatchPayload)
        assertEquals("Test Rider", viewModel.uiState.value.latestEmergencyDispatchPayload?.medicalSummary?.riderName)
        assertEquals(1, viewModel.uiState.value.latestEmergencyDispatchPayload?.emergencyContacts?.size)
    }

    @Test
    fun `stopping monitoring clears latestEmergencyDispatchPayload`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        viewModel.prepareDispatchPayload(incidentTimestamp = 7777L)
        assertNotNull(viewModel.uiState.value.latestEmergencyDispatchPayload)

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull("Dispatch payload must be null when stopped", viewModel.uiState.value.latestEmergencyDispatchPayload)
    }

    @Test
    fun `new monitoring session starts with clean dispatch payload state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Session 1: prepare payload
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        viewModel.prepareDispatchPayload(incidentTimestamp = 8888L)
        assertNotNull(viewModel.uiState.value.latestEmergencyDispatchPayload)

        // Stop session 1
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Session 2: start clean
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull("New session must not inherit previous dispatch payload", viewModel.uiState.value.latestEmergencyDispatchPayload)
    }

    @Test
    fun `prepareDispatchPayload formats and exposes latestEmergencySmsMessage`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.latestEmergencySmsMessage)

        viewModel.prepareDispatchPayload(incidentTimestamp = 9999L)

        val sms = viewModel.uiState.value.latestEmergencySmsMessage
        assertNotNull(sms)
        assertTrue(sms!!.messageText.contains("VAHAKVIGIL EMERGENCY ALERT"))
        assertTrue(sms.characterCount > 0)
        assertTrue(sms.segmentCount >= 1)
    }

    @Test
    fun `stopping monitoring clears latestEmergencySmsMessage`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        viewModel.prepareDispatchPayload(incidentTimestamp = 7777L)
        assertNotNull(viewModel.uiState.value.latestEmergencySmsMessage)

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull("Emergency SMS message must be null when stopped", viewModel.uiState.value.latestEmergencySmsMessage)
    }

    @Test
    fun `new monitoring session starts with clean emergency SMS message state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Session 1: prepare payload and SMS
        viewModel.toggleMonitoring()
        advanceUntilIdle()
        viewModel.prepareDispatchPayload(incidentTimestamp = 8888L)
        assertNotNull(viewModel.uiState.value.latestEmergencySmsMessage)

        // Stop session 1
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Session 2: start clean
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertNull("New session must not inherit previous SMS message", viewModel.uiState.value.latestEmergencySmsMessage)
    }

    @Test
    fun `prepareDispatchPayload retrieves location from locationProvider when configured`() = runTest(testDispatcher) {
        val fakeLocProvider = com.manish.vahakvigil.location.FakeLocationProvider(
            simulatedLocation = EmergencyLocation.Available(12.3456, 78.9101, 3.5f)
        )
        val vmWithLocation = HomeViewModel(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            dispatchDataCompiler = testDispatchDataCompiler,
            locationProvider = fakeLocProvider
        )

        advanceUntilIdle()
        vmWithLocation.toggleMonitoring()
        advanceUntilIdle()

        val payload = vmWithLocation.prepareDispatchPayload(incidentTimestamp = 5555L)

        assertNotNull(payload)
        assertTrue(payload?.location is EmergencyLocation.Available)
        val loc = payload?.location as EmergencyLocation.Available
        assertEquals(12.3456, loc.latitude, 0.0001)
        assertEquals(78.9101, loc.longitude, 0.0001)
        assertEquals(3.5f, loc.accuracyMeters)

        val sms = vmWithLocation.uiState.value.latestEmergencySmsMessage
        assertNotNull(sms)
        assertTrue(sms!!.messageText.contains("Location: 12.3456, 78.9101"))

        // Emergency decision logic must NOT be triggered simply by retrieving location
        assertTrue(vmWithLocation.uiState.value.latestEmergencyDecision?.state != EmergencyDecisionState.EMERGENCY_CANDIDATE)
        assertEquals(EmergencyCountdownState.IDLE, vmWithLocation.uiState.value.emergencyCountdownState)
    }

    @Test
    fun `isEmergencyModeActive is true when decision is EMERGENCY_CANDIDATE and countdown is COUNTING_DOWN`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyModeActive)
        assertFalse(viewModel.uiState.value.userAcknowledgedNeedHelp)

        // Qualify incident
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        // Stillness to trigger EMERGENCY_CANDIDATE
        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyDecisionState.EMERGENCY_CANDIDATE, viewModel.uiState.value.latestEmergencyDecision?.state)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, viewModel.uiState.value.emergencyCountdownState)
        assertTrue(viewModel.uiState.value.isEmergencyModeActive)
        assertFalse(viewModel.uiState.value.userAcknowledgedNeedHelp)
    }

    @Test
    fun `acknowledgeNeedHelp sets userAcknowledgedNeedHelp to true`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.userAcknowledgedNeedHelp)

        viewModel.acknowledgeNeedHelp()
        assertTrue(viewModel.uiState.value.userAcknowledgedNeedHelp)
    }

    @Test
    fun `cancelEmergencyCountdown sets isEmergencyModeActive to false and resets userAcknowledgedNeedHelp`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmergencyModeActive)
        viewModel.acknowledgeNeedHelp()
        assertTrue(viewModel.uiState.value.userAcknowledgedNeedHelp)

        viewModel.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyModeActive)
        assertFalse(viewModel.uiState.value.userAcknowledgedNeedHelp)
        assertEquals(EmergencyCountdownState.CANCELLED, viewModel.uiState.value.emergencyCountdownState)
    }

    @Test
    fun `cancellation exits emergency mode and sets CANCELLED dispatch UI state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyDispatchUiState.COUNTING_DOWN, viewModel.uiState.value.emergencyDispatchUiState)
        assertTrue(viewModel.uiState.value.isEmergencyModeActive)

        viewModel.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.CANCELLED, viewModel.uiState.value.emergencyCountdownState)
        assertEquals(EmergencyDispatchUiState.CANCELLED, viewModel.uiState.value.emergencyDispatchUiState)
    }

    @Test
    fun `countdown expiration is NOT cancellation and keeps emergency mode active`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmergencyModeActive)
        assertEquals(EmergencyDispatchUiState.COUNTING_DOWN, viewModel.uiState.value.emergencyDispatchUiState)

        // Expire countdown
        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.EXPIRED, viewModel.uiState.value.emergencyCountdownState)
        // Expiration MUST keep emergency mode active so post-dispatch UI is displayed!
        assertTrue("Expiration is not cancellation - emergency mode must remain active", viewModel.uiState.value.isEmergencyModeActive)
        assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, viewModel.uiState.value.emergencyDispatchUiState)
        assertEquals(1, fakeActionExecutor.callCount)
    }

    @Test
    fun `failed dispatch reaches DISPATCH_FAILED state without falsely claiming success`() = runTest(testDispatcher) {
        fakeActionExecutor.outcome = EmergencyActionResultStatus.FAILED
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        // Expire countdown
        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.EXPIRED, viewModel.uiState.value.emergencyCountdownState)
        assertTrue(viewModel.uiState.value.isEmergencyModeActive)
        assertEquals(EmergencyDispatchUiState.DISPATCH_FAILED, viewModel.uiState.value.emergencyDispatchUiState)
        assertEquals(EmergencyActionResultStatus.FAILED, viewModel.uiState.value.latestEmergencyActionResult?.status)

        // Retry dispatch through orchestrator
        fakeActionExecutor.outcome = EmergencyActionResultStatus.TRIGGERED
        viewModel.retryEmergencyDispatch()
        advanceUntilIdle()

        assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, viewModel.uiState.value.emergencyDispatchUiState)
        assertEquals(2, fakeActionExecutor.callCount)
    }

    @Test
    fun `successful dispatch cannot be cancelled as if countdown were still active`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify, reach STILL, and expire
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 78_100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, viewModel.uiState.value.emergencyDispatchUiState)

        // Attempting to cancel an expired countdown MUST be a no-op
        viewModel.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.EXPIRED, viewModel.uiState.value.emergencyCountdownState)
        assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, viewModel.uiState.value.emergencyDispatchUiState)
        assertTrue(viewModel.uiState.value.isEmergencyModeActive)

        // Rider safely dismisses via dismissEmergencyMode
        viewModel.dismissEmergencyMode()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyModeActive)
    }

    @Test
    fun `stopping monitoring resets isEmergencyModeActive and userAcknowledgedNeedHelp`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        // Qualify and reach STILL
        dataSource.emitReading(highAccReading(timestamp = 1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(timestamp = 1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(timestamp = 1600L))
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(
                timestamp = 2000L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()
        dataSource.emitReading(
            SensorReading(
                timestamp = 3100L,
                ax = 9.8f, ay = 0f, az = 0f,
                gx = 0.1f, gy = 0f, gz = 0f,
                accelerationMagnitude = 9.8f,
                gyroscopeMagnitude = 0.1f
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmergencyModeActive)
        viewModel.acknowledgeNeedHelp()
        assertTrue(viewModel.uiState.value.userAcknowledgedNeedHelp)

        viewModel.toggleMonitoring()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyModeActive)
        assertFalse(viewModel.uiState.value.userAcknowledgedNeedHelp)
        assertEquals(EmergencyDispatchUiState.IDLE, viewModel.uiState.value.emergencyDispatchUiState)
    }

    @Test
    fun `toggleMonitoring starts and stops monitoring cleanly`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)

        viewModel.toggleMonitoring()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMonitoring)

        viewModel.toggleMonitoring()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)
    }

    // ─── HP-02: Permission Denial Handling Tests ───────────────────────────────

    @Test
    fun `onPermissionsResult with all required permissions granted starts monitoring`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)

        val permissions = mapOf(
            Manifest.permission.SEND_SMS to true,
            Manifest.permission.ACCESS_FINE_LOCATION to true,
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
            "android.permission.POST_NOTIFICATIONS" to true
        )
        viewModel.onPermissionsResult(permissions)
        advanceUntilIdle()

        assertTrue("Monitoring must start when all permissions are granted", viewModel.uiState.value.isMonitoring)
        assertNull(viewModel.uiState.value.permissionExplanation)
        assertTrue(viewModel.uiState.value.permissionLimitations.isEmpty())
        assertTrue(viewModel.uiState.value.isSmsPermissionGranted)
        assertTrue(viewModel.uiState.value.isLocationPermissionGranted)
        assertTrue(viewModel.uiState.value.isNotificationPermissionGranted)
    }

    @Test
    fun `onPermissionsResult with SEND_SMS denied blocks auto-start and provides clear explanation`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)

        val permissions = mapOf(
            Manifest.permission.SEND_SMS to false,
            Manifest.permission.ACCESS_FINE_LOCATION to true,
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
            "android.permission.POST_NOTIFICATIONS" to true
        )
        viewModel.onPermissionsResult(permissions)
        advanceUntilIdle()

        assertFalse("Monitoring must NOT automatically start when SEND_SMS is denied", viewModel.uiState.value.isMonitoring)
        assertNotNull("User must receive a clear explanation", viewModel.uiState.value.permissionExplanation)
        assertTrue(viewModel.uiState.value.permissionExplanation!!.contains("SMS permission is denied"))
        assertFalse(viewModel.uiState.value.isSmsPermissionGranted)
        assertTrue(viewModel.uiState.value.permissionLimitations.any { it.contains("SEND_SMS denied") })
    }

    @Test
    fun `onPermissionsResult with Location denied shows appropriate limitation and allows monitoring`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)

        val permissions = mapOf(
            Manifest.permission.SEND_SMS to true,
            Manifest.permission.ACCESS_FINE_LOCATION to false,
            Manifest.permission.ACCESS_COARSE_LOCATION to false,
            "android.permission.POST_NOTIFICATIONS" to true
        )
        viewModel.onPermissionsResult(permissions)
        advanceUntilIdle()

        assertTrue("Monitoring should not be blocked when only location is denied", viewModel.uiState.value.isMonitoring)
        assertFalse(viewModel.uiState.value.isLocationPermissionGranted)
        assertTrue("Appropriate limitation must be shown", viewModel.uiState.value.permissionLimitations.any { it.contains("Location denied") })
    }

    @Test
    fun `onPermissionsResult with Notification denied maintains honest behavior and allows monitoring`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)

        val permissions = mapOf(
            Manifest.permission.SEND_SMS to true,
            Manifest.permission.ACCESS_FINE_LOCATION to true,
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
            "android.permission.POST_NOTIFICATIONS" to false
        )
        viewModel.onPermissionsResult(permissions)
        advanceUntilIdle()

        assertTrue("Sensor monitoring must not be blocked when notification is denied", viewModel.uiState.value.isMonitoring)
        assertFalse(viewModel.uiState.value.isNotificationPermissionGranted)
        assertTrue("Behavior remains honest about notifications", viewModel.uiState.value.permissionLimitations.any { it.contains("notifications disabled") })
    }

    @Test
    fun `dismissPermissionExplanation clears explanation message`() = runTest(testDispatcher) {
        val permissions = mapOf(
            Manifest.permission.SEND_SMS to false
        )
        viewModel.onPermissionsResult(permissions)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.permissionExplanation)

        viewModel.dismissPermissionExplanation()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.permissionExplanation)
    }

    @Test
    fun `startMonitoringWithLimitations starts monitoring after acknowledging limitations`() = runTest(testDispatcher) {
        val permissions = mapOf(
            Manifest.permission.SEND_SMS to false
        )
        viewModel.onPermissionsResult(permissions)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMonitoring)

        viewModel.startMonitoringWithLimitations()
        advanceUntilIdle()

        assertTrue("Monitoring starts after explicit acknowledgment", viewModel.uiState.value.isMonitoring)
        assertNull(viewModel.uiState.value.permissionExplanation)
        assertFalse("Permission state remains honest", viewModel.uiState.value.isSmsPermissionGranted)
    }

    // ─── Test Action Executor Helper ──────────────────────────────────────────

    class TestActionExecutor(
        var outcome: EmergencyActionResultStatus = EmergencyActionResultStatus.TRIGGERED
    ) : EmergencyActionExecutor {
        var callCount = 0
        override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
            callCount++
            return EmergencyActionResult(
                status = outcome,
                actionType = request.actionType,
                timestamp = request.incidentTimestamp,
                details = "Test execution"
            )
        }
    }
}



