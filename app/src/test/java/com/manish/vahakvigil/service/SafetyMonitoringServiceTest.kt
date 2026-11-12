package com.manish.vahakvigil.service

import com.manish.vahakvigil.data.repository.SensorRepositoryImpl
import com.manish.vahakvigil.domain.analyzer.MotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.EventQualificationEngine
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventAnalysisCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import com.manish.vahakvigil.domain.location.RideSessionRecorder
import com.manish.vahakvigil.data.sensor.RawSensorEventSource
import com.manish.vahakvigil.data.sensor.SensorSessionRecorder
import com.manish.vahakvigil.sensor.FakeSensorDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.manish.vahakvigil.feedback.FakeEmergencyFeedbackController

class FakeWakeLockHandle : WakeLockHandle {
    override var isHeld: Boolean = false
        private set
    var acquireCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set

    override fun acquire() {
        isHeld = true
        acquireCount++
    }

    override fun release() {
        isHeld = false
        releaseCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SafetyMonitoringServiceTest {

    private lateinit var fakeDataSource: FakeSensorDataSource
    private lateinit var sensorRepository: SensorRepositoryImpl
    private lateinit var pipeline: CrashDetectionPipeline
    private lateinit var service: SafetyMonitoringService
    private lateinit var fakeFeedbackController: FakeEmergencyFeedbackController
    private lateinit var fakeWakeLockHandle: FakeWakeLockHandle

    @Before
    fun setUp() {
        fakeDataSource = FakeSensorDataSource(
            capabilities = SensorCapabilities(hasAccelerometer = true, hasGyroscope = true)
        )
        sensorRepository = SensorRepositoryImpl(fakeDataSource)
        val testConfig = MotionDetectionConfig(
            accelerationThresholdMs2 = 10.0f,
            gyroscopeThresholdRads = 2.0f,
            temporalWindowMillis = 2000L
        )
        val testQualificationConfig = EventQualificationConfig(
            minimumConfidenceScore = 0.40f,
            requiredConsecutiveWindows = 2,
            qualificationWindowMillis = 2000L
        )
        val testPostImpactConfig = PostImpactMotionConfig(
            postImpactWindowMillis = 5000L,
            stillnessAccelerationThreshold = 10.5f,
            stillnessGyroscopeThreshold = 0.5f,
            requiredStillnessDurationMillis = 1000L
        )
        val motionAnalyzer = MotionAnalyzer(testConfig)
        val temporalAnalyzer = TemporalMotionAnalyzer(testConfig, motionAnalyzer)
        val qualificationEngine = EventQualificationEngine(testQualificationConfig)
        val postImpactAnalyzer = PostImpactMotionAnalyzer(testPostImpactConfig)

        pipeline = CrashDetectionPipeline(
            sensorRepository = sensorRepository,
            config = testConfig,
            qualificationConfig = testQualificationConfig,
            postImpactConfig = testPostImpactConfig,
            motionAnalyzer = motionAnalyzer,
            temporalAnalyzer = temporalAnalyzer,
            qualificationEngine = qualificationEngine,
            postImpactAnalyzer = postImpactAnalyzer
        )
        fakeFeedbackController = FakeEmergencyFeedbackController()
        fakeWakeLockHandle = FakeWakeLockHandle()
        SafetyMonitoringService.testSensorRepository = sensorRepository
        SafetyMonitoringService.testCrashDetectionPipeline = pipeline
        SafetyMonitoringService.testFeedbackController = fakeFeedbackController
        SafetyMonitoringService.testWakeLockHandle = fakeWakeLockHandle
        // Service lifecycle methods are invoked synchronously in these unit tests.
        // Unconfined execution keeps StateFlow's initial emission ordered before direct test actions.
        SafetyMonitoringService.testServiceDispatcher = Dispatchers.Unconfined
        service = SafetyMonitoringService()
    }

    @After
    fun tearDown() {
        service.onServiceStopped()
        SafetyMonitoringService.testSensorRepository = null
        SafetyMonitoringService.testCrashDetectionPipeline = null
        SafetyMonitoringService.testFeedbackController = null
        SafetyMonitoringService.testWakeLockHandle = null
        SafetyMonitoringService.testServiceDispatcher = null
        SafetyMonitoringService.testRideSessionRecorder = null
        SafetyMonitoringService.testSensorSessionRecorder = null
    }

    @Test
    fun serviceConstants_areConfiguredCorrectly() {
        assertEquals("vahak_vigil_safety_channel", SafetyMonitoringService.CHANNEL_ID)
        assertEquals("Ride Safety Monitoring", SafetyMonitoringService.CHANNEL_NAME)
        assertTrue(SafetyMonitoringService.CHANNEL_DESCRIPTION.isNotEmpty())
        assertEquals("vahak_vigil_emergency_channel", SafetyMonitoringService.EMERGENCY_CHANNEL_ID)
        assertEquals("Emergency Crash Alerts", SafetyMonitoringService.EMERGENCY_CHANNEL_NAME)
        assertTrue(SafetyMonitoringService.EMERGENCY_CHANNEL_DESCRIPTION.isNotEmpty())
        assertNotEquals(SafetyMonitoringService.CHANNEL_ID, SafetyMonitoringService.EMERGENCY_CHANNEL_ID)
        assertTrue(SafetyMonitoringService.NOTIFICATION_ID > 0)
    }

    @Test
    fun serviceActions_areDistinctAndNamespaced() {
        assertNotEquals(SafetyMonitoringService.ACTION_START, SafetyMonitoringService.ACTION_STOP)
        assertTrue(SafetyMonitoringService.ACTION_START.startsWith("com.manish.vahakvigil.action."))
        assertTrue(SafetyMonitoringService.ACTION_STOP.startsWith("com.manish.vahakvigil.action."))
    }

    @Test
    fun notificationBranding_isClearAndConsistent() {
        assertEquals("VahakVigil Active", SafetyMonitoringService.NOTIFICATION_TITLE)
        assertEquals("Ride safety monitoring active in background", SafetyMonitoringService.NOTIFICATION_TEXT)
        assertEquals("Safety System", SafetyMonitoringService.NOTIFICATION_SUBTEXT)
    }

    @Test
    fun initialRunningState_isFalse() {
        assertFalse(SafetyMonitoringService.isServiceRunning.value)
        assertFalse(sensorRepository.isMonitoring.value)
        assertFalse(fakeDataSource.isListening)
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun onServiceStarted_startsSensorRepositoryMonitoringAndPipeline() {
        service.onServiceStarted()

        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(sensorRepository.isMonitoring.value)
        assertTrue(fakeDataSource.isListening)
        assertTrue(pipeline.isRunning)
    }

    @Test
    fun sessionStop_flushesSensorEventsBeforeFinishingRideAndStartsSafetyImmediately() {
        val operations = mutableListOf<String>()
        val rideStore = RecordingRideSessionStore(operations)
        val source = TestRawSensorSource()
        val sensorEventRepository = RecordingSensorEventRepository(operations)
        val rideRecorder = RideSessionRecorder(rideStore, timeProvider = { 2_000L })
        val sensorRecorder = SensorSessionRecorder(
            source = source,
            sensorEventRepository = sensorEventRepository,
            rideSessionRepository = rideStore,
            batchSize = 64,
            retryDelayMillis = 0L,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
        SafetyMonitoringService.testRideSessionRecorder = rideRecorder
        SafetyMonitoringService.testSensorSessionRecorder = sensorRecorder

        service.onServiceStarted()
        assertTrue("Crash monitoring starts before asynchronous persistence", pipeline.isRunning)
        service.onServiceDestroyed()
        val restartedService = SafetyMonitoringService()
        service = restartedService
        restartedService.onServiceStarted()
        assertTrue("START_STICKY recreation keeps monitoring", pipeline.isRunning)
        assertEquals("Recorder remains application-scoped with one collector", 1, source.collectorStartCount)
        source.emit(
            RawSensorEvent(RawSensorType.ACCELEROMETER, 10L, 1L, 1f, 2f, 3f, 3)
        )
        service.onServiceStopped()

        assertEquals(listOf("insert:ride-1:1", "finish:ride-1"), operations)
        assertEquals(1L, sensorRecorder.diagnostics.value.persistedEventCount)
    }

    @Test
    fun sensorRecordingFailure_doesNotStopCrashPipeline() {
        val operations = mutableListOf<String>()
        val rideStore = RecordingRideSessionStore(operations)
        val source = TestRawSensorSource()
        val rideRecorder = RideSessionRecorder(rideStore, timeProvider = { 2_000L })
        val sensorRecorder = SensorSessionRecorder(
            source = source,
            sensorEventRepository = RecordingSensorEventRepository(operations, failInserts = true),
            rideSessionRepository = rideStore,
            batchSize = 1,
            retryDelayMillis = 0L,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
        SafetyMonitoringService.testRideSessionRecorder = rideRecorder
        SafetyMonitoringService.testSensorSessionRecorder = sensorRecorder

        service.onServiceStarted()
        source.emit(RawSensorEvent(RawSensorType.GYROSCOPE, 10L, 1L, 1f, 2f, 3f, 3))

        assertTrue(pipeline.isRunning)
        assertEquals(1L, sensorRecorder.diagnostics.value.failedEventCount)
        assertFalse(operations.any { it.startsWith("finish:") })
        service.onServiceStopped()
    }

    @Test
    fun onServiceStopped_stopsSensorRepositoryMonitoringAndPipeline() {
        service.onServiceStarted()
        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(sensorRepository.isMonitoring.value)
        assertTrue(pipeline.isRunning)

        service.onServiceStopped()
        assertFalse(SafetyMonitoringService.isServiceRunning.value)
        assertFalse(sensorRepository.isMonitoring.value)
        assertFalse(fakeDataSource.isListening)
        assertFalse(pipeline.isRunning)
    }

    @Test
    fun duplicateStartCalls_areIdempotentAndPreventDuplicateListeners() {
        service.onServiceStarted()
        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(sensorRepository.isMonitoring.value)
        assertTrue(pipeline.isRunning)

        // Second call while already running
        service.onServiceStarted()
        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(sensorRepository.isMonitoring.value)
        assertTrue(fakeDataSource.isListening)
        assertTrue(pipeline.isRunning)

        // Calling startMonitoring on repository directly while service is running is also idempotent
        sensorRepository.startMonitoring()
        assertTrue(sensorRepository.isMonitoring.value)
        assertTrue(fakeDataSource.isListening)
    }

    @Test
    fun sensorReadings_streamThroughExistingFlowWhenStartedByService() = runTest {
        service.onServiceStarted()

        val sampleReading = SensorReading(
            timestamp = 99999L,
            ax = 0.5f, ay = 9.8f, az = 1.0f,
            gx = 0.01f, gy = 0.02f, gz = 0.03f
        )

        var collectedReading: SensorReading? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            collectedReading = sensorRepository.sensorReadings.first()
        }

        fakeDataSource.emitReading(sampleReading)
        job.join()

        assertEquals(sampleReading, collectedReading)
    }

    private fun waitForCondition(timeoutMs: Long = 3000L, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun duplicateActionStart_whileMonitoringIsRunning_isIdempotent() {
        service.onServiceStarted()
        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(pipeline.isRunning)

        // Simulate repeated ACTION_START
        service.onServiceStarted()
        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(pipeline.isRunning)
        assertTrue(sensorRepository.isMonitoring.value)
    }

    @Test
    fun actionStart_whileEmergencyCountdownIsActive_leavesEmergencyIntact() = runTest {
        service.onServiceStarted()
        assertTrue(pipeline.isRunning)

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

        assertTrue(waitForCondition { fakeDataSource.subscriptionCount.value > 0 })
        fakeDataSource.emitReading(highAcc(1000L))
        fakeDataSource.emitReading(highGyro(1200L))
        fakeDataSource.emitReading(highAcc(1400L))
        fakeDataSource.emitReading(highGyro(1600L))
        fakeDataSource.emitReading(still(2000L))
        fakeDataSource.emitReading(still(3100L))

        assertTrue(waitForCondition { pipeline.state.value.isEmergencyModeActive })
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)
        val initialQualified = pipeline.state.value.latestQualifiedIncident
        assertNotNull(initialQualified)

        // Simulate duplicate ACTION_START arriving during active emergency countdown
        service.onServiceStarted()

        // Emergency must remain completely intact
        assertTrue(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)
        assertEquals(initialQualified, pipeline.state.value.latestQualifiedIncident)
    }

    @Test
    fun repeatedActionStart_callsRemainCompletelyIdempotent() {
        service.onServiceStarted()
        repeat(5) {
            service.onServiceStarted()
            assertTrue(SafetyMonitoringService.isServiceRunning.value)
            assertTrue(pipeline.isRunning)
            assertTrue(sensorRepository.isMonitoring.value)
        }
    }

    @Test
    fun serviceRestart_reconnectionBehavior_preservesActiveEmergency() = runTest {
        service.onServiceStarted()

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

        assertTrue(waitForCondition { fakeDataSource.subscriptionCount.value > 0 })
        fakeDataSource.emitReading(highAcc(1000L))
        fakeDataSource.emitReading(highGyro(1200L))
        fakeDataSource.emitReading(highAcc(1400L))
        fakeDataSource.emitReading(highGyro(1600L))
        fakeDataSource.emitReading(still(2000L))
        fakeDataSource.emitReading(still(3100L))

        assertTrue(waitForCondition { pipeline.state.value.isEmergencyModeActive })

        // Simulate unexpected service destruction (e.g. system low memory kill, not ACTION_STOP)
        service.onServiceDestroyed()

        // Active emergency state must be preserved
        assertTrue(pipeline.state.value.isEmergencyModeActive)

        // Simulate START_STICKY service restart (new service instance started)
        val recreatedService = SafetyMonitoringService()
        recreatedService.onServiceStarted()

        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(pipeline.isRunning)
        assertTrue(pipeline.state.value.isEmergencyModeActive)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, pipeline.state.value.emergencyCountdownState)

        recreatedService.onServiceStopped()
    }

    // ─── CB-02: Partial WakeLock Tests ──────────────────────────────────────────

    @Test
    fun wakeLock_isAcquired_whenMonitoringStarts() {
        assertFalse(fakeWakeLockHandle.isHeld)
        assertEquals(0, fakeWakeLockHandle.acquireCount)

        service.onServiceStarted()

        assertTrue("WakeLock must be held when monitoring starts", fakeWakeLockHandle.isHeld)
        assertEquals(1, fakeWakeLockHandle.acquireCount)
    }

    @Test
    fun wakeLock_isReleased_whenMonitoringStops() {
        service.onServiceStarted()
        assertTrue(fakeWakeLockHandle.isHeld)

        service.onServiceStopped()

        assertFalse("WakeLock must be released when monitoring stops", fakeWakeLockHandle.isHeld)
        assertEquals(1, fakeWakeLockHandle.releaseCount)
    }

    @Test
    fun wakeLock_isReleased_whenServiceDestroyed() {
        service.onServiceStarted()
        assertTrue(fakeWakeLockHandle.isHeld)

        service.onServiceDestroyed()

        assertFalse("WakeLock must be released when service is destroyed", fakeWakeLockHandle.isHeld)
        assertEquals(1, fakeWakeLockHandle.releaseCount)
    }

    @Test
    fun wakeLock_repeatedStartCalls_doNotDuplicateLockAcquisition() {
        repeat(5) {
            service.onServiceStarted()
        }
        assertTrue(fakeWakeLockHandle.isHeld)
        assertEquals("WakeLock must only be acquired once (non-reference counted)", 1, fakeWakeLockHandle.acquireCount)
    }

    // ─── CB-01: Background Emergency Feedback Tests ─────────────────────────────

    @Test
    fun emergencyFeedback_starts_whenCountdownBegins() {
        assertFalse(fakeFeedbackController.isAlerting)
        assertEquals(0, fakeFeedbackController.startAlertCallCount)

        val countdownState = com.manish.vahakvigil.domain.model.CrashDetectionState(
            emergencyCountdownState = EmergencyCountdownState.COUNTING_DOWN,
            emergencyCountdownRemainingMillis = 60_000L
        )
        service.handlePipelineEmergencyState(countdownState)

        assertTrue("Emergency feedback must be active during countdown", fakeFeedbackController.isAlerting)
        assertEquals(1, fakeFeedbackController.startAlertCallCount)
    }

    @Test
    fun emergencyFeedback_stops_afterCancellationOrCompletion() {
        val countdownState = com.manish.vahakvigil.domain.model.CrashDetectionState(
            emergencyCountdownState = EmergencyCountdownState.COUNTING_DOWN,
            emergencyCountdownRemainingMillis = 60_000L
        )
        service.handlePipelineEmergencyState(countdownState)
        assertTrue(fakeFeedbackController.isAlerting)

        val cancelledState = com.manish.vahakvigil.domain.model.CrashDetectionState(
            emergencyCountdownState = EmergencyCountdownState.CANCELLED,
            emergencyCountdownRemainingMillis = 75_000L
        )
        service.handlePipelineEmergencyState(cancelledState)

        assertFalse("Emergency feedback must cease upon cancellation", fakeFeedbackController.isAlerting)
        assertEquals(1, fakeFeedbackController.stopAlertCallCount)
    }

    @Test
    fun repeatedCountdownStateEmissions_doNotDuplicateFeedback() {
        val countdownState = com.manish.vahakvigil.domain.model.CrashDetectionState(
            emergencyCountdownState = EmergencyCountdownState.COUNTING_DOWN,
            emergencyCountdownRemainingMillis = 60_000L
        )
        repeat(5) {
            service.handlePipelineEmergencyState(countdownState)
        }

        assertTrue(fakeFeedbackController.isAlerting)
        assertEquals("Feedback alert must start exactly once", 1, fakeFeedbackController.startAlertCallCount)
    }

    @Test
    fun serviceStopped_stopsEmergencyFeedback_ifActive() {
        val countdownState = com.manish.vahakvigil.domain.model.CrashDetectionState(
            emergencyCountdownState = EmergencyCountdownState.COUNTING_DOWN,
            emergencyCountdownRemainingMillis = 60_000L
        )
        service.handlePipelineEmergencyState(countdownState)
        assertTrue(fakeFeedbackController.isAlerting)

        service.onServiceStopped()

        assertFalse("Emergency feedback must cease when service stops", fakeFeedbackController.isAlerting)
        assertEquals(1, fakeFeedbackController.stopAlertCallCount)
    }

    // ─── LP-01: Task-Removal and START_STICKY Hardening Tests ──────────────────

    @Test
    fun handleTaskRemoved_whileMonitoring_preservesMonitoringStateAndWakeLock() {
        service.onServiceStarted()
        assertTrue(SafetyMonitoringService.isServiceRunning.value)
        assertTrue(pipeline.isRunning)
        assertTrue(fakeWakeLockHandle.isHeld)

        service.handleTaskRemoved(null)

        assertTrue("Monitoring must remain active after task removal", SafetyMonitoringService.isServiceRunning.value)
        assertTrue("Pipeline must continue running after task removal", pipeline.isRunning)
        assertTrue("WakeLock must remain held after task removal", fakeWakeLockHandle.isHeld)
    }

    @Test
    fun handleTaskRemoved_duringEmergency_preservesEmergencyState() {
        service.onServiceStarted()
        repeat(20) {
            val countdownState = com.manish.vahakvigil.domain.model.CrashDetectionState(
                emergencyCountdownState = EmergencyCountdownState.COUNTING_DOWN,
                emergencyCountdownRemainingMillis = 60_000L
            )
            service.handlePipelineEmergencyState(countdownState)
            assertTrue(fakeFeedbackController.isAlerting)

            service.handleTaskRemoved(null)

            assertTrue(fakeFeedbackController.isAlerting)
            assertTrue(SafetyMonitoringService.isServiceRunning.value)
            assertTrue(pipeline.isRunning)
        }
    }

    @Test
    fun unexpectedDestruction_preservesPipelineRunningForStartSticky() {
        service.onServiceStarted()
        service.isExplicitlyStopped = false
        assertTrue(pipeline.isRunning)

        service.onServiceDestroyed()

        assertTrue("Pipeline must remain running on unexpected destruction so START_STICKY can resume it", pipeline.isRunning)
        assertFalse("Service instance running state is cleared", SafetyMonitoringService.isServiceRunning.value)
        assertFalse("WakeLock is released on destruction", fakeWakeLockHandle.isHeld)
    }

    @Test
    fun explicitStopRide_causesNullIntentToReturnStartNotSticky() {
        service.isExplicitlyStopped = true
        val result = service.onStartCommand(null, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    private class TestRawSensorSource : RawSensorEventSource {
        private val events = MutableSharedFlow<RawSensorEvent>(extraBufferCapacity = 32)
        var collectorStartCount: Int = 0
            private set
        override val rawSensorEvents: Flow<RawSensorEvent> = kotlinx.coroutines.flow.flow {
            collectorStartCount++
            events.collect { emit(it) }
        }
        override val droppedEventCount: Long = 0L
        fun emit(event: RawSensorEvent) { events.tryEmit(event) }
    }

    private class RecordingSensorEventRepository(
        private val operations: MutableList<String>,
        private val failInserts: Boolean = false
    ) : SensorEventRepository {
        override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) {
            operations += "insert:$sessionId:${events.size}"
            if (failInserts) error("disk unavailable")
        }
        override suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int) = emptyList<RawSensorEvent>()
        override suspend fun countForSession(sessionId: String) = 0L
        override suspend fun getExportSnapshot(sessionId: String) = SensorEventExportSnapshot(0L, 0L)
        override suspend fun getExportPage(
            sessionId: String,
            maxEventId: Long,
            after: SensorEventExportCursor?,
            limit: Int
        ): List<RecordedSensorEvent> = emptyList()
        override suspend fun getAnalysisPage(
            sessionId: String,
            maxEventId: Long,
            after: SensorEventAnalysisCursor,
            limit: Int
        ): List<RecordedSensorEvent> = emptyList()
    }

    private class RecordingRideSessionStore(
        private val operations: MutableList<String>
    ) : RideSessionRepository {
        private var current = RideSession(
            id = "ride-1", startedAtEpochMillis = 1_000L, endedAtEpochMillis = null,
            startLatitude = null, startLongitude = null, endLatitude = null, endLongitude = null,
            durationMillis = 0L, distanceMeters = null, averageSpeedMetersPerSecond = null,
            maximumSpeedMetersPerSecond = null, status = RideSessionStatus.ACTIVE
        )
        private val sessions = kotlinx.coroutines.flow.MutableStateFlow(listOf(current))
        override fun observeSessions(): Flow<List<RideSession>> = sessions.asStateFlow()
        override fun observeSession(id: String): Flow<RideSession?> = kotlinx.coroutines.flow.MutableStateFlow(current.takeIf { it.id == id })
        override suspend fun getSession(id: String): RideSession? = current.takeIf { it.id == id }
        override suspend fun getActiveSession(): RideSession? = current.takeIf { it.status == RideSessionStatus.ACTIVE }
        override suspend fun startOrResume(startedAtEpochMillis: Long): RideSession = current
        override suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean = false
        override suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean): Boolean {
            operations += "finish:$sessionId"
            current = current.copy(
                endedAtEpochMillis = endedAtEpochMillis,
                status = if (interrupted) RideSessionStatus.INTERRUPTED else RideSessionStatus.INCOMPLETE
            )
            sessions.value = listOf(current)
            return true
        }
        override suspend fun getPoints(sessionId: String): List<RideSessionPoint> = emptyList()
        override suspend fun deleteSession(sessionId: String) = Unit
    }
}
