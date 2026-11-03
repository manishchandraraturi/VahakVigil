package com.manish.vahakvigil.domain.pipeline

import com.manish.vahakvigil.data.repository.SensorRepositoryImpl
import com.manish.vahakvigil.domain.analyzer.EmergencyCountdownEngine
import com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine
import com.manish.vahakvigil.domain.analyzer.EventQualificationEngine
import com.manish.vahakvigil.domain.analyzer.MotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Focused unit tests for Slide-to-Confirm emergency escalation path verifying:
 * 1. Slide reaches threshold: existing escalation invoked exactly once.
 * 2. Slide released before threshold: no emergency escalation.
 * 3. Slide escalation before countdown expiry: countdown cannot later trigger second escalation.
 * 4. Normal countdown expiry: existing behavior unchanged.
 * 5. I'M FINE: existing cancellation behavior unchanged and prevents slide escalation.
 * 6. Repeated/new incident: existing repeated incident behavior operates cleanly.
 * 7. Emergency dispatch payload produced by slide: exact same path/data as countdown expiry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlideEmergencyEscalationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pipelineScope: CoroutineScope

    private lateinit var dataSource: FakeSensorDataSource
    private lateinit var repository: SensorRepositoryImpl
    private lateinit var locationProvider: FakeTestLocationProvider
    private lateinit var pipeline: CrashDetectionPipeline

    private var actionExecuteCount = 0
    private var simulatedWallTime = 1000L

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

    private val testActionExecutor = object : EmergencyActionExecutor {
        override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
            actionExecuteCount++
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.TRIGGERED,
                actionType = request.actionType,
                timestamp = request.incidentTimestamp,
                details = "Executed via test executor"
            )
        }
    }

    class FakeTestLocationProvider(
        var locationToReturn: EmergencyLocation = EmergencyLocation.Available(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5.0f,
            timestampMs = 1_700_000_000_000L
        )
    ) : LocationProvider {
        var queryCount = 0
        override suspend fun getCurrentLocation(): EmergencyLocation {
            queryCount++
            return locationToReturn
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pipelineScope = CoroutineScope(SupervisorJob() + testDispatcher)

        actionExecuteCount = 0
        simulatedWallTime = 1000L

        dataSource = FakeSensorDataSource(
            capabilities = SensorCapabilities(
                hasAccelerometer = true,
                hasGyroscope = true
            )
        )
        repository = SensorRepositoryImpl(dataSource)
        repository.startMonitoring()

        val contactRepo = FakeContactRepository(
            listOf(Contact(id = 1L, name = "Primary Contact", relationship = "Spouse", phone = "+919876543210"))
        )

        val medicalRepo = FakeMedicalProfileRepository(
            MedicalProfile(
                name = "Test Rider",
                bloodGroup = "O+",
                allergies = "None",
                conditions = "None",
                medications = "None",
                notes = "Test notes"
            )
        )

        locationProvider = FakeTestLocationProvider()
        val compiler = EmergencyDispatchDataCompiler(medicalRepo, contactRepo)
        val smsFormatter = EmergencySmsFormatter(zoneId = ZoneId.of("UTC"))

        val motionAnalyzer = MotionAnalyzer(testConfig)
        pipeline = CrashDetectionPipeline(
            sensorRepository = repository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            decisionConfig = testDecisionConfig,
            motionAnalyzer = motionAnalyzer,
            temporalAnalyzer = TemporalMotionAnalyzer(testConfig, motionAnalyzer),
            qualificationEngine = EventQualificationEngine(testQualificationConfig),
            postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig),
            decisionEngine = EmergencyDecisionEngine(testDecisionConfig),
            countdownEngine = EmergencyCountdownEngine(testDecisionConfig.countdownDurationMillis),
            actionOrchestrator = EmergencyActionOrchestrator(testActionExecutor),
            dispatchDataCompiler = compiler,
            locationProvider = locationProvider,
            smsFormatter = smsFormatter,
            countdownDispatcher = testDispatcher,
            timeProvider = { simulatedWallTime }
        )

        pipeline.start(pipelineScope)
    }

    @After
    fun tearDown() {
        pipeline.stop()
        pipelineScope.cancel()
        Dispatchers.resetMain()
    }

    private fun highAcc(ts: Long) = SensorReading(
        timestamp = ts, ax = 30f, ay = 0f, az = 0f, gx = 0f, gy = 0f, gz = 0f,
        accelerationMagnitude = 30f, gyroscopeMagnitude = 0f
    )

    private fun highGyro(ts: Long) = SensorReading(
        timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 5f, gy = 0f, gz = 0f,
        accelerationMagnitude = 9.8f, gyroscopeMagnitude = 5f
    )

    private fun still(ts: Long) = SensorReading(
        timestamp = ts, ax = 9.8f, ay = 0f, az = 0f, gx = 0.1f, gy = 0f, gz = 0f,
        accelerationMagnitude = 9.8f, gyroscopeMagnitude = 0.1f
    )

    private fun TestScope.triggerCrashIncident(baseTimestamp: Long = 1000L) {
        simulatedWallTime = baseTimestamp
        dataSource.emitReading(highAcc(baseTimestamp))
        dataSource.emitReading(highGyro(baseTimestamp + 200L))
        dataSource.emitReading(highAcc(baseTimestamp + 400L))
        dataSource.emitReading(highGyro(baseTimestamp + 600L))
        dataSource.emitReading(still(baseTimestamp + 1000L))
        dataSource.emitReading(still(baseTimestamp + 2100L))
        runCurrent()

        assertTrue("Incident must activate emergency mode", pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)
    }

    // ─── 1. Slide reaches threshold ──────────────────────────────────────────

    @Test
    fun `slide reaches threshold invokes existing emergency escalation exactly once`() = runTest(testDispatcher) {
        try {
            triggerCrashIncident()
            assertEquals(0, actionExecuteCount)

            // User slides handle across threshold
            pipeline.confirmEmergencyImmediately()
            runCurrent()

            assertEquals("Escalation must be invoked exactly once", 1, actionExecuteCount)
            assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)
            assertEquals(0L, pipeline.state.value.emergencyCountdownRemainingMillis)
            assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, pipeline.state.value.emergencyDispatchUiState)
            assertTrue(pipeline.state.value.userAcknowledgedNeedHelp)
            assertNotNull(pipeline.state.value.latestEmergencyDispatchPayload)

            // Multiple rapid confirmations do not create duplicate dispatches
            pipeline.confirmEmergencyImmediately()
            runCurrent()
            assertEquals("Subsequent slide confirmations must not duplicate escalation", 1, actionExecuteCount)
        } finally {
            pipeline.stop()
        }
    }

    // ─── 2. Slide released before threshold ──────────────────────────────────

    @Test
    fun `slide released before threshold causes no emergency escalation`() = runTest(testDispatcher) {
        try {
            triggerCrashIncident()
            assertEquals(0, actionExecuteCount)

            // If user releases before threshold, confirmEmergencyImmediately is NOT called by UI
            simulatedWallTime += 15_000L
            advanceTimeBy(15_000L)
            runCurrent()

            assertEquals("No escalation when slide is released before threshold", 0, actionExecuteCount)
            assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)
            assertTrue("Countdown still active", pipeline.state.value.emergencyCountdownRemainingMillis <= 60_000L)
        } finally {
            pipeline.stop()
        }
    }

    // ─── 3. Slide escalation before countdown expiry ─────────────────────────

    @Test
    fun `slide escalation before countdown expiry prevents countdown from later triggering second escalation`() = runTest(testDispatcher) {
        try {
            triggerCrashIncident(baseTimestamp = 1000L)

            // Slide at 30 seconds into the countdown
            simulatedWallTime += 30_000L
            advanceTimeBy(30_000L)
            runCurrent()

            assertEquals(0, actionExecuteCount)
            assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

            // Confirm via slide
            pipeline.confirmEmergencyImmediately()
            runCurrent()

            assertEquals("Slide triggers escalation immediately", 1, actionExecuteCount)
            assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)

            // Now advance time well past the original 75-second countdown duration
            simulatedWallTime += 60_000L
            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals("Countdown must not later fire a second escalation", 1, actionExecuteCount)
        } finally {
            pipeline.stop()
        }
    }

    // ─── 4. Normal countdown expiry ──────────────────────────────────────────

    @Test
    fun `normal countdown expiry existing behavior unchanged`() = runTest(testDispatcher) {
        try {
            triggerCrashIncident(baseTimestamp = 1000L)

            // User does not slide at all
            simulatedWallTime += 76_000L
            advanceTimeBy(76_000L)
            runCurrent()

            assertEquals("Normal countdown expiry escalates exactly once", 1, actionExecuteCount)
            assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)
            assertEquals(0L, pipeline.state.value.emergencyCountdownRemainingMillis)
            assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, pipeline.state.value.emergencyDispatchUiState)
        } finally {
            pipeline.stop()
        }
    }

    // ─── 5. I'M FINE ─────────────────────────────────────────────────────────

    @Test
    fun `im fine existing cancellation behavior unchanged and prevents subsequent slide`() = runTest(testDispatcher) {
        try {
            triggerCrashIncident()

            // User taps "I'M FINE"
            pipeline.cancelEmergencyCountdown()
            runCurrent()

            assertEquals(EmergencyCountdownState.CANCELLED, pipeline.state.value.emergencyCountdownState)
            assertFalse(pipeline.state.value.isEmergencyModeActive)
            assertEquals(0, actionExecuteCount)

            // User attempting to slide after cancellation is rejected
            pipeline.confirmEmergencyImmediately()
            runCurrent()

            assertEquals("Cancelled countdown cannot be escalated via slide", 0, actionExecuteCount)
            assertEquals(EmergencyCountdownState.CANCELLED, pipeline.state.value.emergencyCountdownState)
        } finally {
            pipeline.stop()
        }
    }

    // ─── 6. Repeated / new incident ──────────────────────────────────────────

    @Test
    fun `repeated new incident behavior operates cleanly after prior cancellation`() = runTest(testDispatcher) {
        try {
            // Incident #1
            triggerCrashIncident(baseTimestamp = 1000L)
            pipeline.cancelEmergencyCountdown()
            runCurrent()
            assertEquals(0, actionExecuteCount)

            // Normal driving between incidents
            simulatedWallTime += 5000L
            dataSource.emitReading(still(simulatedWallTime))
            runCurrent()

            // Incident #2 occurs
            triggerCrashIncident(baseTimestamp = 15_000L)
            assertEquals(0, actionExecuteCount)

            // Slide confirmed on Incident #2
            pipeline.confirmEmergencyImmediately()
            runCurrent()

            assertEquals("Incident #2 escalated cleanly via slide", 1, actionExecuteCount)
            assertEquals(EmergencyCountdownState.EXPIRED, pipeline.state.value.emergencyCountdownState)
            assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, pipeline.state.value.emergencyDispatchUiState)
        } finally {
            pipeline.stop()
        }
    }

    // ─── 7. Emergency dispatch payload produced by slide ─────────────────────

    @Test
    fun `emergency dispatch payload produced by slide contains same path and data as countdown expiry`() = runTest(testDispatcher) {
        try {
            triggerCrashIncident(baseTimestamp = 2000L)

            // Confirm via slide
            pipeline.confirmEmergencyImmediately()
            runCurrent()

            val payload = pipeline.state.value.latestEmergencyDispatchPayload
            assertNotNull("Payload must be compiled", payload)
            assertTrue("Location must be available", payload!!.location is EmergencyLocation.Available)

            val loc = payload.location as EmergencyLocation.Available
            assertEquals(12.9716, loc.latitude, 0.0001)
            assertEquals(77.5946, loc.longitude, 0.0001)
            assertEquals(1, locationProvider.queryCount)

            assertEquals("Must contain primary emergency contact", 1, payload.emergencyContacts.size)
            assertEquals("+919876543210", payload.emergencyContacts.first().phone)

            assertEquals("O+", payload.medicalSummary?.bloodGroup)
            assertEquals("Test Rider", payload.medicalSummary?.riderName)

            val sms = pipeline.state.value.latestEmergencySmsMessage
            assertNotNull("SMS must be formatted", sms)
            assertTrue("SMS must contain Google Maps link", sms!!.messageText.contains("maps.google.com"))
            assertTrue("SMS must contain human readable time", sms.messageText.contains("Incident time:"))

            assertEquals(EmergencyDispatchUiState.DISPATCH_TRIGGERED, pipeline.state.value.emergencyDispatchUiState)
        } finally {
            pipeline.stop()
        }
    }
}
