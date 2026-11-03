package com.manish.vahakvigil.domain.pipeline

import com.manish.vahakvigil.data.repository.SensorRepositoryImpl
import com.manish.vahakvigil.domain.analyzer.EmergencyCountdownEngine
import com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine
import com.manish.vahakvigil.domain.analyzer.EventQualificationEngine
import com.manish.vahakvigil.domain.analyzer.MotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.orchestration.EmergencyActionExecutor
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import com.manish.vahakvigil.sensor.FakeSensorDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class CrashDetectionPipelineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pipelineScope: CoroutineScope

    private lateinit var dataSource: FakeSensorDataSource
    private lateinit var repository: SensorRepositoryImpl
    private lateinit var pipeline: CrashDetectionPipeline

    private val testConfig = MotionDetectionConfig(
        accelerationThresholdMs2 = 10.0f,
        gyroscopeThresholdRads = 2.0f,
        temporalWindowMillis = 2000L
    )

    private val testQualificationConfig = EventQualificationConfig(
        minimumConfidenceScore = 0.40f,
        requiredConsecutiveWindows = 2,
        qualificationWindowMillis = 2000L
    )

    private val testPostImpactConfig = PostImpactMotionConfig(
        postImpactWindowMillis = 5000L,
        stillnessAccelerationThreshold = 10.5f,
        stillnessGyroscopeThreshold = 0.5f,
        requiredStillnessDurationMillis = 1000L
    )

    private val testDecisionConfig = EmergencyDecisionConfig(
        countdownDurationMillis = 75_000L
    )

    private var actionExecuteCount = 0
    private val testActionExecutor = object : EmergencyActionExecutor {
        override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
            actionExecuteCount++
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.TRIGGERED,
                actionType = request.actionType,
                timestamp = request.incidentTimestamp,
                details = "Test execution"
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pipelineScope = CoroutineScope(testDispatcher + SupervisorJob())

        dataSource = FakeSensorDataSource(
            capabilities = SensorCapabilities(hasAccelerometer = true, hasGyroscope = true)
        )
        repository = SensorRepositoryImpl(dataSource)
        repository.startMonitoring()

        val motionAnalyzer = MotionAnalyzer(testConfig)
        val temporalAnalyzer = TemporalMotionAnalyzer(testConfig, motionAnalyzer)
        val qualificationEngine = EventQualificationEngine(testQualificationConfig)
        val postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig)
        val decisionEngine = EmergencyDecisionEngine(testDecisionConfig)
        val countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis)
        val orchestrator = EmergencyActionOrchestrator(testActionExecutor)

        val medicalRepo = FakeMedicalProfileRepository(
            MedicalProfile(name = "Test Rider", bloodGroup = "O+", allergies = "", conditions = "", medications = "", notes = "")
        )
        val contactRepo = FakeContactRepository(
            listOf(Contact(id = 1L, name = "Primary Contact", relationship = "Spouse", phone = "9876543210"))
        )
        val compiler = EmergencyDispatchDataCompiler(medicalRepo, contactRepo)

        pipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = motionAnalyzer,
            temporalAnalyzer = temporalAnalyzer,
            qualificationEngine = qualificationEngine,
            postImpactAnalyzer = postImpactAnalyzer,
            decisionEngine = decisionEngine,
            countdownEngine = countdownEngine,
            actionOrchestrator = orchestrator,
            dispatchDataCompiler = compiler
        )
    }

    @After
    fun tearDown() {
        pipeline.stop()
        pipelineScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun pipeline_lifecycle_startsAndStopsCleanly() = runTest(testDispatcher) {
        assertFalse(pipeline.isRunning)

        pipeline.start(pipelineScope)
        advanceUntilIdle()
        assertTrue(pipeline.isRunning)

        pipeline.stop()
        advanceUntilIdle()
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun pipeline_consumesSensorReadings_whenRunning() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        val reading = SensorReading(
            timestamp = 1000L,
            ax = 1.0f, ay = 2.0f, az = 9.8f,
            gx = 0.01f, gy = 0.02f, gz = 0.03f
        )
        dataSource.emitReading(reading)
        advanceUntilIdle()

        assertEquals(reading, pipeline.state.value.latestReading)
        assertNotNull(pipeline.state.value.latestMotionEvent)

        pipeline.stop()
    }

    @Test
    fun pipeline_stop_clearsIntermediateStateAndCancelsCollection() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        dataSource.emitReading(
            SensorReading(timestamp = 1000L, ax = 1f, ay = 2f, az = 9.8f, gx = 0f, gy = 0f, gz = 0f)
        )
        advanceUntilIdle()
        assertNotNull(pipeline.state.value.latestReading)

        pipeline.stop()
        advanceUntilIdle()

        assertNull(pipeline.state.value.latestReading)
        assertEquals(EmergencyCountdownState.IDLE, pipeline.state.value.emergencyCountdownState)

        // Further readings after stop are ignored
        dataSource.emitReading(
            SensorReading(timestamp = 2000L, ax = 3f, ay = 4f, az = 9.8f, gx = 0f, gy = 0f, gz = 0f)
        )
        advanceUntilIdle()
        assertNull(pipeline.state.value.latestReading)
    }

    @Test
    fun pipeline_emergencyActions_operateCorrectly() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        // Test acknowledgeNeedHelp
        assertFalse(pipeline.state.value.userAcknowledgedNeedHelp)
        pipeline.acknowledgeNeedHelp()
        assertTrue(pipeline.state.value.userAcknowledgedNeedHelp)

        // Test cancelEmergencyCountdown ("I'm Fine")
        pipeline.cancelEmergencyCountdown()
        assertEquals(EmergencyCountdownState.IDLE, pipeline.state.value.emergencyCountdownState)

        // Test dismissEmergencyMode
        pipeline.dismissEmergencyMode()
        assertFalse(pipeline.state.value.isEmergencyModeActive)

        pipeline.stop()
    }

    @Test
    fun multiIncident_afterCancel_rearmsAndDetectsSecondIncidentCleanly() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAccReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 30f, ay = 0f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f,
            gyroscopeMagnitude = 0f
        )

        fun highGyroReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 5f
        )

        fun stillReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 0.1f
        )

        // ── Incident #1 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(1000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(1200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(1400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(1600L))
        advanceUntilIdle()

        // Stillness observation
        dataSource.emitReading(stillReading(2000L))
        advanceUntilIdle()
        dataSource.emitReading(stillReading(3100L))
        advanceUntilIdle()

        // Verify Incident #1 is active
        assertTrue(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        // User taps "I'm Fine"
        pipeline.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertFalse(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.CANCELLED, pipeline.state.value.emergencyCountdownState)

        // ── Normal readings in between ──────────────────────────────
        dataSource.emitReading(stillReading(5000L))
        advanceUntilIdle()
        assertFalse(pipeline.state.value.isEmergencyModeActive)

        // ── Incident #2 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(15000L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(15200L))
        advanceUntilIdle()
        dataSource.emitReading(highAccReading(15400L))
        advanceUntilIdle()
        dataSource.emitReading(highGyroReading(15600L))
        advanceUntilIdle()

        // Stillness observation for Incident #2
        dataSource.emitReading(stillReading(16000L))
        advanceUntilIdle()
        dataSource.emitReading(stillReading(17100L))
        advanceUntilIdle()

        // Verify Incident #2 qualified and started a new countdown session!
        assertTrue("Incident #2 must activate emergency mode", pipeline.state.value.isEmergencyModeActive)
        assertEquals(
            "Incident #2 must start new countdown",
            EmergencyCountdownState.COUNTING_DOWN,
            pipeline.state.value.emergencyCountdownState
        )

        pipeline.stop()
    }

    @Test
    fun multiIncident_threeConsecutiveIncidents_withCancellations_rearmAndDetectCleanly() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAccReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 30f, ay = 0f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f,
            gyroscopeMagnitude = 0f
        )
        fun highGyroReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 5f
        )
        fun stillReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 0.1f
        )

        // ── Incident #1 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(1000L))
        dataSource.emitReading(highGyroReading(1200L))
        dataSource.emitReading(highAccReading(1400L))
        dataSource.emitReading(highGyroReading(1600L))
        dataSource.emitReading(stillReading(2000L))
        dataSource.emitReading(stillReading(3100L))
        advanceUntilIdle()

        assertTrue("Incident #1 must trigger emergency", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        pipeline.cancelEmergencyCountdown()
        advanceUntilIdle()
        assertFalse(pipeline.state.value.isEmergencyModeActive)

        // ── Incident #2 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(10000L))
        dataSource.emitReading(highGyroReading(10200L))
        dataSource.emitReading(highAccReading(10400L))
        dataSource.emitReading(highGyroReading(10600L))
        dataSource.emitReading(stillReading(11000L))
        dataSource.emitReading(stillReading(12100L))
        advanceUntilIdle()

        assertTrue("Incident #2 must trigger emergency", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        pipeline.cancelEmergencyCountdown()
        advanceUntilIdle()
        assertFalse(pipeline.state.value.isEmergencyModeActive)

        // ── Incident #3 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(20000L))
        dataSource.emitReading(highGyroReading(20200L))
        dataSource.emitReading(highAccReading(20400L))
        dataSource.emitReading(highGyroReading(20600L))
        dataSource.emitReading(stillReading(21000L))
        dataSource.emitReading(stillReading(22100L))
        advanceUntilIdle()

        assertTrue("Incident #3 must trigger emergency", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        pipeline.cancelEmergencyCountdown()
        advanceUntilIdle()
        assertFalse(pipeline.state.value.isEmergencyModeActive)

        pipeline.stop()
    }

    @Test
    fun multiIncident_afterTimeoutAndDismiss_rearmsAndDetectsSecondIncidentCleanly() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAccReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 30f, ay = 0f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f,
            gyroscopeMagnitude = 0f
        )
        fun highGyroReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 5f
        )
        fun stillReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 0.1f
        )

        // ── Incident #1 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(1000L))
        dataSource.emitReading(highGyroReading(1200L))
        dataSource.emitReading(highAccReading(1400L))
        dataSource.emitReading(highGyroReading(1600L))
        dataSource.emitReading(stillReading(2000L))
        dataSource.emitReading(stillReading(3100L))
        advanceUntilIdle()

        assertTrue(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        // Advance reading beyond 75s countdown
        dataSource.emitReading(stillReading(80000L))
        advanceUntilIdle()

        assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)
        assertTrue("Action must be triggered upon expiration", actionExecuteCount >= 1)

        // Rider dismisses post-expiration screen
        pipeline.dismissEmergencyMode()
        advanceUntilIdle()

        assertFalse(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.IDLE, pipeline.state.value.emergencyCountdownState)

        // ── Incident #2 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(100000L))
        dataSource.emitReading(highGyroReading(100200L))
        dataSource.emitReading(highAccReading(100400L))
        dataSource.emitReading(highGyroReading(100600L))
        dataSource.emitReading(stillReading(101000L))
        dataSource.emitReading(stillReading(102100L))
        advanceUntilIdle()

        assertTrue("Incident #2 after dismiss must trigger emergency", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        pipeline.stop()
    }

    @Test
    fun multiIncident_afterNeedHelpAndDismiss_rearmsAndDetectsSecondIncidentCleanly() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAccReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 30f, ay = 0f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f,
            gyroscopeMagnitude = 0f
        )
        fun highGyroReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 5f
        )
        fun stillReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 0.1f
        )

        // ── Incident #1 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(1000L))
        dataSource.emitReading(highGyroReading(1200L))
        dataSource.emitReading(highAccReading(1400L))
        dataSource.emitReading(highGyroReading(1600L))
        dataSource.emitReading(stillReading(2000L))
        dataSource.emitReading(stillReading(3100L))
        advanceUntilIdle()

        pipeline.acknowledgeNeedHelp()
        assertTrue(pipeline.state.value.userAcknowledgedNeedHelp)

        // Countdown expires
        dataSource.emitReading(stillReading(80000L))
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)

        // Dismiss
        pipeline.dismissEmergencyMode()
        advanceUntilIdle()
        assertFalse(pipeline.state.value.userAcknowledgedNeedHelp)

        // ── Incident #2 ─────────────────────────────────────────────
        dataSource.emitReading(highAccReading(100000L))
        dataSource.emitReading(highGyroReading(100200L))
        dataSource.emitReading(highAccReading(100400L))
        dataSource.emitReading(highGyroReading(100600L))
        dataSource.emitReading(stillReading(101000L))
        dataSource.emitReading(stillReading(102100L))
        advanceUntilIdle()

        assertTrue(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)
        assertFalse("New incident must have userAcknowledgedNeedHelp reset", pipeline.state.value.userAcknowledgedNeedHelp)

        pipeline.stop()
    }

    @Test
    fun pipeline_stopAndRestart_cleansSessionAndDetectsIncident() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAccReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 30f, ay = 0f, az = 0f,
            gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f,
            gyroscopeMagnitude = 0f
        )
        fun highGyroReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 5f
        )
        fun stillReading(ts: Long) = SensorReading(
            timestamp = ts,
            ax = 9.8f, ay = 0f, az = 0f,
            gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f,
            gyroscopeMagnitude = 0.1f
        )

        // Incident #1
        dataSource.emitReading(highAccReading(1000L))
        dataSource.emitReading(highGyroReading(1200L))
        dataSource.emitReading(highAccReading(1400L))
        dataSource.emitReading(highGyroReading(1600L))
        dataSource.emitReading(stillReading(2000L))
        dataSource.emitReading(stillReading(3100L))
        advanceUntilIdle()

        assertTrue(pipeline.state.value.isEmergencyModeActive)

        // Stop ride
        pipeline.stop()
        advanceUntilIdle()

        assertFalse(pipeline.state.value.isEmergencyModeActive)
        assertNull(pipeline.state.value.latestQualifiedIncident)

        // Start ride again
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        // Incident #2 in fresh session
        dataSource.emitReading(highAccReading(50000L))
        dataSource.emitReading(highGyroReading(50200L))
        dataSource.emitReading(highAccReading(50400L))
        dataSource.emitReading(highGyroReading(50600L))
        dataSource.emitReading(stillReading(51000L))
        dataSource.emitReading(stillReading(52100L))
        advanceUntilIdle()

        assertTrue("New session must detect incident cleanly", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        pipeline.stop()
    }

    @Test
    fun cancellationConcurrentWithIncidentProcessing_safelyCancelsAndPreventsResurrection() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        assertTrue(pipeline.state.value.isEmergencyModeActive)

        // Cancel on main thread while continuous readings flow
        pipeline.cancelEmergencyCountdown()

        dataSource.emitReading(still(3200L))
        dataSource.emitReading(still(3300L))
        dataSource.emitReading(still(3400L))
        advanceUntilIdle()

        assertFalse("Emergency mode must remain cancelled", pipeline.state.value.isEmergencyModeActive)
        assertTrue(
            "Countdown must not be active",
            pipeline.state.value.emergencyCountdownState == EmergencyCountdownState.CANCELLED ||
            pipeline.state.value.emergencyCountdownState == EmergencyCountdownState.IDLE
        )
        assertNull("Qualified incident must remain cleared", pipeline.state.value.latestQualifiedIncident)

        pipeline.stop()
    }

    @Test
    fun dismissalConcurrentWithIncidentProcessing_safelyClearsEmergency() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        // Expire countdown
        dataSource.emitReading(still(80000L))
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)

        // Dismiss while readings are being emitted
        pipeline.dismissEmergencyMode()
        dataSource.emitReading(still(80100L))
        dataSource.emitReading(still(80200L))
        advanceUntilIdle()

        assertFalse("Emergency mode must be dismissed", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.IDLE, pipeline.state.value.emergencyCountdownState)
        assertEquals(com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.IDLE, pipeline.state.value.emergencyDispatchUiState)

        pipeline.stop()
    }

    @Test
    fun multipleIncidentTransitions_underConcurrentCallbacks_rearmsCleanly() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        // Incident #1
        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        assertTrue(pipeline.state.value.isEmergencyModeActive)

        // User cancels
        pipeline.cancelEmergencyCountdown()
        advanceUntilIdle()
        assertFalse(pipeline.state.value.isEmergencyModeActive)

        // Incident #2 after cancellation
        dataSource.emitReading(highAcc(50000L))
        dataSource.emitReading(highGyro(50200L))
        dataSource.emitReading(highAcc(50400L))
        dataSource.emitReading(highGyro(50600L))
        dataSource.emitReading(still(51000L))
        dataSource.emitReading(still(52100L))
        advanceUntilIdle()

        assertTrue("Incident #2 must trigger a fresh countdown", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        pipeline.stop()
    }

    @Test
    fun duplicateDispatch_remainsPrevented_evenWithConcurrentOrRepeatedReadings() = runTest(testDispatcher) {
        pipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        // Expire countdown
        dataSource.emitReading(still(80000L))
        advanceUntilIdle()
        assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)
        assertEquals("Initial dispatch must occur exactly once", 1, actionExecuteCount)

        // Multiple subsequent readings while expired must NOT re-dispatch
        repeat(10) { i ->
            dataSource.emitReading(still(81000L + (i * 100L)))
        }
        advanceUntilIdle()

        assertEquals("Subsequent readings must never trigger duplicate dispatch", 1, actionExecuteCount)

        pipeline.stop()
    }

    // =========================================================================
    // F-04 Tests: Wall-Clock Countdown Independence
    // =========================================================================

    @Test
    fun countdown_progressesAndExpiresWithoutNewSensorReadings_accordingToWallClock() = runTest(testDispatcher) {
        var simulatedWallTime = 1000L
        var localActionCount = 0
        val localActionExecutor = object : EmergencyActionExecutor {
            override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
                localActionCount++
                return EmergencyActionResult(
                    status = EmergencyActionResultStatus.TRIGGERED,
                    actionType = request.actionType,
                    timestamp = request.incidentTimestamp,
                    details = "F-04 test"
                )
            }
        }

        val wallClockPipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = MotionAnalyzer(testConfig),
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, MotionAnalyzer(testConfig)),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(localActionExecutor),
            countdownDispatcher = testDispatcher,
            timeProvider = { simulatedWallTime }
        )
        wallClockPipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        runCurrent()

        assertTrue(wallClockPipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, wallClockPipeline.state.value.emergencyCountdownState)
        assertEquals(75_000L, wallClockPipeline.state.value.emergencyCountdownRemainingMillis)

        // ZERO NEW SENSOR READINGS EMITTED BEYOND THIS POINT!
        // Advance wall-clock by 30 seconds
        simulatedWallTime += 30_000L
        advanceTimeBy(30_000L)
        runCurrent()

        // Countdown must progress smoothly without any sensor readings
        assertTrue("Remaining time must decrease without sensor readings",
            wallClockPipeline.state.value.emergencyCountdownRemainingMillis <= 45_000L)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, wallClockPipeline.state.value.emergencyCountdownState)

        // Advance wall-clock past 75 seconds total
        simulatedWallTime += 46_000L
        advanceTimeBy(46_000L)
        runCurrent()

        // Countdown must expire at the expected wall-clock time
        assertEquals(EmergencyCountdownState.EXPIRED, wallClockPipeline.state.value.emergencyCountdownState)
        assertEquals(0L, wallClockPipeline.state.value.emergencyCountdownRemainingMillis)
        assertEquals("Emergency dispatch must execute upon expiration", 1, localActionCount)

        wallClockPipeline.stop()
    }

    @Test
    fun countdown_cancellationAndImFine_withoutSensorReadings_stopsCountdownAndAllowsSecondIncident() = runTest(testDispatcher) {
        var simulatedWallTime = 1000L
        var localActionCount = 0
        val localActionExecutor = object : EmergencyActionExecutor {
            override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
                localActionCount++
                return EmergencyActionResult(
                    status = EmergencyActionResultStatus.TRIGGERED,
                    actionType = request.actionType,
                    timestamp = request.incidentTimestamp,
                    details = "F-04 test"
                )
            }
        }

        val wallClockPipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = MotionAnalyzer(testConfig),
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, MotionAnalyzer(testConfig)),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(localActionExecutor),
            countdownDispatcher = testDispatcher,
            timeProvider = { simulatedWallTime }
        )
        wallClockPipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        runCurrent()

        assertTrue(wallClockPipeline.state.value.isEmergencyModeActive)

        // Advance 20 seconds
        simulatedWallTime += 20_000L
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(wallClockPipeline.state.value.emergencyCountdownRemainingMillis <= 55_000L)

        // Rider presses "I'M FINE"
        wallClockPipeline.cancelEmergencyCountdown()
        runCurrent()

        assertFalse(wallClockPipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.CANCELLED, wallClockPipeline.state.value.emergencyCountdownState)

        // Advance another 80 seconds
        simulatedWallTime += 80_000L
        advanceTimeBy(80_000L)
        runCurrent()

        // Action must NEVER trigger after cancellation
        assertEquals("Cancelled incident must never trigger action", 0, localActionCount)

        // Incident #2 triggers afterward
        dataSource.emitReading(highAcc(110000L))
        dataSource.emitReading(highGyro(110200L))
        dataSource.emitReading(highAcc(110400L))
        dataSource.emitReading(highGyro(110600L))
        dataSource.emitReading(still(111000L))
        dataSource.emitReading(still(112100L))
        runCurrent()

        assertTrue("Incident #2 must trigger emergency mode", wallClockPipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, wallClockPipeline.state.value.emergencyCountdownState)

        wallClockPipeline.stop()
    }

    // =========================================================================
    // F-06 Tests: GPS / Sensor Processing Isolation
    // =========================================================================

    @Test
    fun gpsDelay_doesNotBlockSensorProcessing_andStateRemainsCorrect() = runTest(testDispatcher) {
        val simulatedGpsDelayMs = 2000L
        val delayedLocationProvider = object : LocationProvider {
            override suspend fun getCurrentLocation(): EmergencyLocation {
                delay(simulatedGpsDelayMs)
                return EmergencyLocation.Available(
                    latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = 1000L
                )
            }
        }

        var localActionCount = 0
        val localActionExecutor = object : EmergencyActionExecutor {
            override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
                localActionCount++
                return EmergencyActionResult(
                    status = EmergencyActionResultStatus.TRIGGERED,
                    actionType = request.actionType,
                    timestamp = request.incidentTimestamp,
                    details = "F-06 test"
                )
            }
        }

        val isolationPipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = MotionAnalyzer(testConfig),
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, MotionAnalyzer(testConfig)),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(localActionExecutor),
            locationProvider = delayedLocationProvider
        )
        isolationPipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        // Expire countdown to trigger dispatch with slow GPS
        dataSource.emitReading(still(80000L))
        advanceTimeBy(100L) // GPS starts acquiring, suspended for 2000ms

        assertEquals(EmergencyCountdownState.EXPIRED, isolationPipeline.state.value.emergencyCountdownState)
        assertEquals(EmergencyDispatchUiState.DISPATCHING, isolationPipeline.state.value.emergencyDispatchUiState)
        assertEquals("Dispatch not completed yet while GPS in flight", 0, localActionCount)

        // WHILE GPS IS STILL SUSPENDED: Emit new sensor readings
        val lateReading = SensorReading(
            timestamp = 81000L, ax = 1.0f, ay = 9.8f, az = 0.5f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.86f, gyroscopeMagnitude = 0f
        )
        dataSource.emitReading(lateReading)
        advanceTimeBy(100L)

        // Sensor processing MUST NOT BE BLOCKED: latestReading must update!
        assertEquals("Sensor reading was processed during GPS delay", lateReading, isolationPipeline.state.value.latestReading)

        // Complete the GPS delay
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertEquals("Dispatch completed after GPS finished", 1, localActionCount)
        assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, isolationPipeline.state.value.emergencyDispatchUiState)
        assertEquals(
            "Dispatch action result belongs to the qualified incident timestamp",
            1400L,
            isolationPipeline.state.value.latestEmergencyActionResult?.timestamp
        )

        // Additional readings after dispatch must not cause duplicate dispatch
        dataSource.emitReading(still(82000L))
        dataSource.emitReading(still(83000L))
        advanceUntilIdle()
        assertEquals("Duplicate dispatch must remain impossible", 1, localActionCount)

        isolationPipeline.stop()
    }

    @Test
    fun cancellationDuringGpsPreparation_isHandledSafely_andSubsequentIncidentWorks() = runTest(testDispatcher) {
        val delayedLocationProvider = object : LocationProvider {
            override suspend fun getCurrentLocation(): EmergencyLocation {
                delay(3000L)
                return EmergencyLocation.Available(
                    latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = 1000L
                )
            }
        }

        var localActionCount = 0
        val localActionExecutor = object : EmergencyActionExecutor {
            override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
                localActionCount++
                return EmergencyActionResult(
                    status = EmergencyActionResultStatus.TRIGGERED,
                    actionType = request.actionType,
                    timestamp = request.incidentTimestamp,
                    details = "F-06 test"
                )
            }
        }

        val isolationPipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = MotionAnalyzer(testConfig),
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, MotionAnalyzer(testConfig)),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(localActionExecutor),
            locationProvider = delayedLocationProvider
        )
        isolationPipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        // Expire countdown -> starts GPS acquisition
        dataSource.emitReading(still(80000L))
        advanceTimeBy(100L)
        assertEquals(EmergencyDispatchUiState.DISPATCHING, isolationPipeline.state.value.emergencyDispatchUiState)

        // Cancel countdown WHILE GPS acquisition is in-flight
        isolationPipeline.cancelEmergencyCountdown()
        advanceUntilIdle()

        // State is cancelled, no action executed
        assertFalse(isolationPipeline.state.value.isEmergencyModeActive)
        assertEquals(0, localActionCount)

        // Complete the remaining GPS delay
        advanceTimeBy(3000L)
        advanceUntilIdle()

        // Must still have 0 dispatches because it was cancelled!
        assertEquals("Cancelled dispatch must never trigger after GPS completes", 0, localActionCount)

        // Subsequent Incident #2
        dataSource.emitReading(highAcc(100000L))
        dataSource.emitReading(highGyro(100200L))
        dataSource.emitReading(highAcc(100400L))
        dataSource.emitReading(highGyro(100600L))
        dataSource.emitReading(still(101000L))
        dataSource.emitReading(still(102100L))
        advanceUntilIdle()

        assertTrue("Incident #2 triggers emergency cleanly", isolationPipeline.state.value.isEmergencyModeActive)

        isolationPipeline.stop()
    }

    @Test
    fun gpsTimeout_emergencyDispatchStillCompletes_withUnavailableLocation() = runTest(testDispatcher) {
        val hangingLocationProvider = object : LocationProvider {
            override suspend fun getCurrentLocation(): EmergencyLocation {
                delay(10_000L) // Exceeds 5000ms timeout
                return EmergencyLocation.Available(
                    latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = 1000L
                )
            }
        }

        var localActionCount = 0
        lateinit var isolationPipeline: CrashDetectionPipeline
        val localActionExecutor = object : EmergencyActionExecutor {
            override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
                localActionCount++
                return EmergencyActionResult(
                    status = EmergencyActionResultStatus.TRIGGERED,
                    actionType = request.actionType,
                    timestamp = request.incidentTimestamp,
                    details = "GPS timeout test"
                )
            }
        }

        val medicalRepo = FakeMedicalProfileRepository(
            MedicalProfile(name = "Test Rider", bloodGroup = "O+", allergies = "", conditions = "", medications = "", notes = "")
        )
        val contactRepo = FakeContactRepository(
            listOf(Contact(id = 1L, name = "Primary Contact", relationship = "Spouse", phone = "9876543210"))
        )
        val compiler = EmergencyDispatchDataCompiler(medicalRepo, contactRepo)

        isolationPipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = MotionAnalyzer(testConfig),
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, MotionAnalyzer(testConfig)),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(localActionExecutor),
            locationProvider = hangingLocationProvider,
            dispatchDataCompiler = compiler
        )
        isolationPipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        // Expire countdown -> starts GPS acquisition (hanging for 10s, timeout is 5s)
        dataSource.emitReading(still(80000L))
        advanceTimeBy(100L)

        assertEquals(EmergencyDispatchUiState.DISPATCHING, isolationPipeline.state.value.emergencyDispatchUiState)
        assertEquals("Dispatch not completed yet before timeout", 0, localActionCount)

        // Advance 5000ms for timeout to fire
        advanceTimeBy(5000L)
        advanceUntilIdle()

        assertEquals("Dispatch must complete after 5s GPS timeout", 1, localActionCount)
        assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, isolationPipeline.state.value.emergencyDispatchUiState)
        assertEquals(EmergencyLocation.Unavailable, isolationPipeline.state.value.latestEmergencyDispatchPayload?.location)

        isolationPipeline.stop()
    }

    @Test
    fun staleOrCancelledIncident_cannotDispatch_afterGpsTimeout() = runTest(testDispatcher) {
        val hangingLocationProvider = object : LocationProvider {
            override suspend fun getCurrentLocation(): EmergencyLocation {
                delay(10_000L) // Exceeds 5000ms timeout
                return EmergencyLocation.Available(
                    latitude = 12.9716, longitude = 77.5946, accuracyMeters = 5f, timestampMs = 1000L
                )
            }
        }

        var localActionCount = 0
        val localActionExecutor = object : EmergencyActionExecutor {
            override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
                localActionCount++
                return EmergencyActionResult(
                    status = EmergencyActionResultStatus.TRIGGERED,
                    actionType = request.actionType,
                    timestamp = request.incidentTimestamp,
                    details = "Stale timeout test"
                )
            }
        }

        val isolationPipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = MotionAnalyzer(testConfig),
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, MotionAnalyzer(testConfig)),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(localActionExecutor),
            locationProvider = hangingLocationProvider
        )
        isolationPipeline.start(pipelineScope)
        advanceUntilIdle()

        fun highAcc(ts: Long) = SensorReading(
            timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
            accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
        )
        fun highGyro(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
        )
        fun still(ts: Long) = SensorReading(
            timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
            accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
        )

        dataSource.emitReading(highAcc(1000L))
        dataSource.emitReading(highGyro(1200L))
        dataSource.emitReading(highAcc(1400L))
        dataSource.emitReading(highGyro(1600L))
        dataSource.emitReading(still(2000L))
        dataSource.emitReading(still(3100L))
        advanceUntilIdle()

        // Expire countdown -> starts GPS acquisition
        dataSource.emitReading(still(80000L))
        advanceTimeBy(100L)
        assertEquals(EmergencyDispatchUiState.DISPATCHING, isolationPipeline.state.value.emergencyDispatchUiState)

        // Cancel countdown during GPS wait
        isolationPipeline.cancelEmergencyCountdown()
        advanceUntilIdle()

        assertFalse(isolationPipeline.state.value.isEmergencyModeActive)
        assertEquals(0, localActionCount)

        // Advance past 5s timeout and past 10s delay
        advanceTimeBy(10_000L)
        advanceUntilIdle()

        // Must still have 0 dispatches because it was cancelled!
        assertEquals("Cancelled/stale incident must never dispatch after GPS timeout", 0, localActionCount)

        isolationPipeline.stop()
    }
}
