package com.manish.vahakvigil.domain.pipeline

import com.manish.vahakvigil.domain.analyzer.ConfidenceScorer
import com.manish.vahakvigil.domain.analyzer.EmergencyCountdownEngine
import com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine
import com.manish.vahakvigil.domain.analyzer.EventQualificationEngine
import com.manish.vahakvigil.domain.analyzer.MotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.ConfidenceScoringConfig
import com.manish.vahakvigil.domain.model.ConfidenceScoringInput
import com.manish.vahakvigil.domain.model.CrashDetectionState
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyLifecycleClock
import com.manish.vahakvigil.domain.model.JvmEmergencyLifecycleClock
import com.manish.vahakvigil.domain.model.EmergencyLifecycleRecord
import com.manish.vahakvigil.domain.model.EmergencyLifecyclePhase
import com.manish.vahakvigil.domain.model.EmergencyDispatchAttemptState
import com.manish.vahakvigil.domain.model.EmergencyUserResponse
import com.manish.vahakvigil.domain.model.EmergencyCountdownRecovery
import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.QualifiedIncident
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionWindow
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionWindow
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.repository.SensorRepository
import com.manish.vahakvigil.domain.repository.EmergencyLifecycleRepository
import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Domain-level coordinator executing the complete VahakVigil crash detection,
 * qualification, stillness evaluation, countdown, and emergency orchestration pipeline.
 *
 * Runs independently of the Android UI/Activity lifecycle, driven by a persistent [CoroutineScope]
 * (e.g. from [com.manish.vahakvigil.service.SafetyMonitoringService]).
 */
class CrashDetectionPipeline(
    private val sensorRepository: SensorRepository,
    private val config: MotionDetectionConfig = MotionDetectionConfig(),
    private val scoringConfig: ConfidenceScoringConfig = ConfidenceScoringConfig.DEFAULT,
    private val qualificationConfig: EventQualificationConfig = EventQualificationConfig.DEFAULT,
    private val postImpactConfig: PostImpactMotionConfig = PostImpactMotionConfig.DEFAULT,
    private val decisionConfig: EmergencyDecisionConfig = EmergencyDecisionConfig.DEFAULT,
    private val motionAnalyzer: MotionAnalyzer = MotionAnalyzer(config),
    private val temporalAnalyzer: TemporalMotionAnalyzer = TemporalMotionAnalyzer(config, motionAnalyzer),
    private val confidenceScorer: ConfidenceScorer = ConfidenceScorer(scoringConfig),
    private val qualificationEngine: EventQualificationEngine = EventQualificationEngine(qualificationConfig),
    private val postImpactAnalyzer: PostImpactMotionAnalyzer = PostImpactMotionAnalyzer(postImpactConfig),
    private val decisionEngine: EmergencyDecisionEngine = EmergencyDecisionEngine(decisionConfig),
    private val countdownEngine: EmergencyCountdownEngine = EmergencyCountdownEngine(decisionConfig.countdownDurationMillis),
    private val actionOrchestrator: EmergencyActionOrchestrator = EmergencyActionOrchestrator(),
    private val dispatchDataCompiler: EmergencyDispatchDataCompiler? = null,
    private val smsFormatter: EmergencySmsFormatter = EmergencySmsFormatter(),
    private val locationProvider: LocationProvider? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val countdownDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val emergencyLifecycleRepository: EmergencyLifecycleRepository? = null,
    private val emergencyLifecycleClock: EmergencyLifecycleClock = JvmEmergencyLifecycleClock
) {


    private val _state = MutableStateFlow(
        CrashDetectionState(
            emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis
        )
    )
    val state: StateFlow<CrashDetectionState> = _state.asStateFlow()

    private var readingsJob: Job? = null
    private var activeScope: CoroutineScope? = null
    private var countdownTickerJob: Job? = null
    private var dispatchJob: Job? = null
    private var lifecycleStartupJob: Job? = null
    private var activeLifecycleIncidentId: String? = null
    private var pendingLifecycleRecord: EmergencyLifecycleRecord? = null
    private var lifecycleQualifiedIncident: QualifiedIncident? = null
    private var pendingDispatchAfterRecovery = false
    private val lifecycleMutex = Mutex()
    @Volatile private var isDispatchInProgress: Boolean = false

    val isRunning: Boolean
        get() = readingsJob?.isActive == true || lifecycleStartupJob?.isActive == true

    /**
     * Bounded chronological buffer of recent sensor readings for temporal motion analysis.
     */
    private val readingBuffer = ArrayDeque<SensorReading>()

    /**
     * Bounded buffer of post-impact sensor readings collected after a [QualifiedIncident].
     */
    private val postImpactBuffer = ArrayDeque<SensorReading>()

    /**
     * Records whether the rider explicitly dismissed the post-expiration emergency UI.
     */
    private var userDismissedEmergencyMode: Boolean = false

    /**
     * Domain identity of the currently active qualified incident, e.g. "${windowStartMs}_${windowEndMs}".
     * Distinguishes the ongoing incident from newly qualifying incidents.
     */
    private var activeIncidentIdentity: String? = null

    /**
     * Identity of the most recently closed (cancelled or dismissed) incident, preventing stale re-qualification.
     */
    private var closedIncidentIdentity: String? = null

    /**
     * Starts the crash detection collection loop within the provided [coroutineScope].
     * If a collection job is already active on another scope, it is explicitly cancelled
     * and rebound to [coroutineScope].
     */
    @Synchronized
    fun start(coroutineScope: CoroutineScope) {
        if (isRunning) {
            return
        }

        activeScope = coroutineScope
        readingsJob?.cancel()

        if (emergencyLifecycleRepository != null && activeIncidentIdentity == null && !_state.value.isEmergencyModeActive) {
            lifecycleStartupJob = coroutineScope.launch {
                restoreEmergencyLifecycle()
                synchronized(this@CrashDetectionPipeline) { startMonitoring(coroutineScope) }
                if (pendingDispatchAfterRecovery) {
                    pendingDispatchAfterRecovery = false
                    val incidentId = activeIncidentIdentity
                    val timestamp = _state.value.latestEmergencyDecision?.incidentTimestampMs ?: timeProvider()
                    if (incidentId != null) triggerEmergencyDispatch(timestamp, incidentId)
                }
            }
            return
        }

        startMonitoring(coroutineScope)
    }

    private fun startMonitoring(coroutineScope: CoroutineScope) {

        val hasActiveEmergency = _state.value.isEmergencyModeActive || activeIncidentIdentity != null
        if (!hasActiveEmergency) {
            readingBuffer.clear()
            postImpactBuffer.clear()
            qualificationEngine.reset()
            countdownEngine.reset()
            actionOrchestrator.reset()
            activeIncidentIdentity = null
            lifecycleQualifiedIncident = null
            closedIncidentIdentity = null
            userDismissedEmergencyMode = false
        } else if (countdownEngine.state == EmergencyCountdownState.COUNTING_DOWN) {
            startCountdownTicker(timeProvider())
        }

        sensorRepository.startMonitoring()

        readingsJob = coroutineScope.launch {
            sensorRepository.sensorReadings.collect { reading ->
                processReading(reading)
            }
        }
    }

    private suspend fun restoreEmergencyLifecycle() {
        val repository = emergencyLifecycleRepository ?: return
        val record = try { repository.getLatest() } catch (_: Exception) { null } ?: return
        if (record.phase == EmergencyLifecyclePhase.CANCELLED) {
            _state.update {
                it.copy(
                    emergencyCountdownState = EmergencyCountdownState.CANCELLED,
                    emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis,
                    emergencyDispatchUiState = EmergencyDispatchUiState.CANCELLED,
                    emergencyIncidentId = record.incidentId,
                    isEmergencyModeActive = false
                )
            }
            return
        }
        if (record.phase == EmergencyLifecyclePhase.COMPLETED) {
            _state.update {
                it.copy(
                    emergencyCountdownState = EmergencyCountdownState.IDLE,
                    emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis,
                    emergencyDispatchUiState = EmergencyDispatchUiState.IDLE,
                    emergencyIncidentId = record.incidentId,
                    isEmergencyModeActive = false
                )
            }
            return
        }
        activeLifecycleIncidentId = record.incidentId
        activeIncidentIdentity = "${record.windowStartEpochMillis}_${record.windowEndEpochMillis}"
        val nowWall = emergencyLifecycleClock.wallClockMillis()
        val nowElapsed = emergencyLifecycleClock.elapsedRealtimeMillis()
        val remaining = EmergencyCountdownRecovery.remainingMillis(
            record, nowWall, nowElapsed, emergencyLifecycleClock.bootCount(), decisionConfig.countdownDurationMillis
        )
        val recoveredPhase = if (record.dispatchState == EmergencyDispatchAttemptState.ATTEMPTED) {
            repository.updateOutcome(record.incidentId, EmergencyLifecyclePhase.DISPATCH_UNKNOWN, EmergencyDispatchAttemptState.UNKNOWN, nowWall)
            EmergencyLifecyclePhase.DISPATCH_UNKNOWN
        } else if (record.phase == EmergencyLifecyclePhase.COUNTING_DOWN && remaining > 0L) {
            EmergencyLifecyclePhase.COUNTING_DOWN
        } else if (record.dispatchState == EmergencyDispatchAttemptState.SENT) {
            EmergencyLifecyclePhase.DISPATCH_TRIGGERED
        } else if (record.dispatchState == EmergencyDispatchAttemptState.FAILED) {
            EmergencyLifecyclePhase.DISPATCH_FAILED
        } else if (record.dispatchState == EmergencyDispatchAttemptState.UNKNOWN) {
            EmergencyLifecyclePhase.DISPATCH_UNKNOWN
        } else {
            EmergencyLifecyclePhase.DISPATCHING
        }

        val windowStart = record.windowStartEpochMillis
        val windowEnd = record.windowEndEpochMillis
        val confidence = ConfidenceScore.fromRaw(record.confidenceScore)
        val candidate = CandidateAccidentEvent(
            windowStart, windowEnd, confidence,
            TemporalMotionEvent(TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE, windowStart, windowEnd, 0f, 0f, 1), 1
        )
        val qualified = QualifiedIncident(windowStart, windowEnd, confidence, 1, listOf(candidate))
        lifecycleQualifiedIncident = qualified
        val decision = com.manish.vahakvigil.domain.model.EmergencyDecision(
            EmergencyDecisionState.EMERGENCY_CANDIDATE, "Recovered emergency lifecycle", windowEnd, PostImpactStatus.STILL
        )
        val activeCountdown = recoveredPhase == EmergencyLifecyclePhase.COUNTING_DOWN
        val startTime = nowWall - (decisionConfig.countdownDurationMillis - remaining)
        countdownEngine.restore(startTime, nowWall)
        if (!activeCountdown) countdownEngine.expireImmediately()

        val dispatchUiState = when (recoveredPhase) {
            EmergencyLifecyclePhase.COUNTING_DOWN -> EmergencyDispatchUiState.COUNTING_DOWN
            EmergencyLifecyclePhase.DISPATCHING -> EmergencyDispatchUiState.DISPATCHING
            EmergencyLifecyclePhase.DISPATCH_TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
            EmergencyLifecyclePhase.DISPATCH_FAILED -> EmergencyDispatchUiState.DISPATCH_FAILED
            EmergencyLifecyclePhase.DISPATCH_UNKNOWN -> EmergencyDispatchUiState.DISPATCH_UNKNOWN
            else -> EmergencyDispatchUiState.IDLE
        }
        val result = when (record.dispatchState) {
            EmergencyDispatchAttemptState.SENT -> EmergencyActionResult(
                EmergencyActionResultStatus.TRIGGERED, com.manish.vahakvigil.domain.model.EmergencyActionType.SMS,
                record.dispatchResolvedAtEpochMillis ?: record.dispatchAttemptedAtEpochMillis ?: nowWall,
                "SMS request accepted by Android messaging system; handset delivery is unconfirmed",
                com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.SENT_TO_MESSAGING_SYSTEM
            )
            EmergencyDispatchAttemptState.FAILED -> EmergencyActionResult(
                EmergencyActionResultStatus.FAILED, com.manish.vahakvigil.domain.model.EmergencyActionType.SMS,
                record.dispatchResolvedAtEpochMillis ?: nowWall, "Emergency SMS request failed"
            )
            EmergencyDispatchAttemptState.UNKNOWN -> EmergencyActionResult(
                EmergencyActionResultStatus.FAILED, com.manish.vahakvigil.domain.model.EmergencyActionType.SMS,
                record.dispatchResolvedAtEpochMillis ?: nowWall, "SMS request outcome is unknown; it was not resent",
                com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN
            )
            EmergencyDispatchAttemptState.ATTEMPTED -> EmergencyActionResult(
                EmergencyActionResultStatus.FAILED, com.manish.vahakvigil.domain.model.EmergencyActionType.SMS,
                record.dispatchAttemptedAtEpochMillis ?: nowWall, "SMS request outcome is unknown; it was not resent",
                com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN
            )
            else -> null
        }
        _state.update {
            it.copy(
                latestQualifiedIncident = qualified,
                latestEmergencyDecision = decision,
                emergencyCountdownState = if (activeCountdown) EmergencyCountdownState.COUNTING_DOWN else EmergencyCountdownState.EXPIRED,
                emergencyCountdownRemainingMillis = if (activeCountdown) remaining else 0L,
                latestEmergencyActionResult = result,
                isEmergencyModeActive = true,
                emergencyDispatchUiState = dispatchUiState,
                userAcknowledgedNeedHelp = record.userResponse == EmergencyUserResponse.NEED_HELP,
                emergencyIncidentId = record.incidentId
            )
        }
        if (activeCountdown) {
            startCountdownTicker(nowWall)
        } else if (record.dispatchState == EmergencyDispatchAttemptState.NOT_STARTED) {
            pendingDispatchAfterRecovery = true
        }
    }

    private fun newLifecycleRecord(phase: EmergencyLifecyclePhase, response: EmergencyUserResponse): EmergencyLifecycleRecord? {
        val id = activeLifecycleIncidentId ?: return null
        val incident = lifecycleQualifiedIncident ?: _state.value.latestQualifiedIncident ?: return null
        val nowWall = emergencyLifecycleClock.wallClockMillis()
        val remaining = countdownEngine.remainingMillis.coerceIn(0L, decisionConfig.countdownDurationMillis)
        val elapsed = emergencyLifecycleClock.elapsedRealtimeMillis()
        return EmergencyLifecycleRecord(
            incidentId = id,
            incidentCreatedAtEpochMillis = nowWall - (decisionConfig.countdownDurationMillis - remaining),
            windowStartEpochMillis = incident.windowStartMs,
            windowEndEpochMillis = incident.windowEndMs,
            confidenceScore = incident.qualifyingConfidenceScore.value,
            phase = phase,
            deadlineElapsedRealtimeMillis = elapsed + remaining,
            deadlineEpochMillis = nowWall + remaining,
            bootCount = emergencyLifecycleClock.bootCount(),
            userResponse = response
        )
    }

    private fun persistLifecyclePhase(phase: EmergencyLifecyclePhase, response: EmergencyUserResponse) {
        val repository = emergencyLifecycleRepository ?: return
        val record = newLifecycleRecord(phase, response) ?: pendingLifecycleRecord ?: return
        val scope = activeScope ?: return
        scope.launch {
            lifecycleMutex.withLock {
                try {
                    repository.createIfMissing(record)
                    repository.updatePhaseAndResponse(record.incidentId, phase, response)
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * Stops the crash detection loop, cancels collection, resets all intermediate buffers,
     * and terminates sensor monitoring on [sensorRepository].
     */
    @Synchronized
    fun stop() {
        readingsJob?.cancel()
        readingsJob = null
        countdownTickerJob?.cancel()
        countdownTickerJob = null
        dispatchJob?.cancel()
        dispatchJob = null
        isDispatchInProgress = false
        activeScope = null

        readingBuffer.clear()
        postImpactBuffer.clear()
        qualificationEngine.reset()
        countdownEngine.reset()
        actionOrchestrator.reset()
        activeIncidentIdentity = null
        activeLifecycleIncidentId = null
        lifecycleQualifiedIncident = null
        closedIncidentIdentity = null
        userDismissedEmergencyMode = false

        sensorRepository.stopMonitoring()

        _state.update {
            CrashDetectionState(
                emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis
            )
        }
    }

    private suspend fun processReading(reading: SensorReading) {
        val dispatchTrigger: Pair<Long, String?>? = synchronized(this) {
            if (readingsJob?.isActive != true) return

            // ── Step 1: Per-reading analysis ─────────────────────────────
            val motionEvent = motionAnalyzer.analyze(reading)

            // ── Step 2: Update bounded buffer ────────────────────────────
            readingBuffer.addLast(reading)
            val windowCutoff = reading.timestamp - config.temporalWindowMillis
            while (readingBuffer.isNotEmpty() && readingBuffer.first().timestamp < windowCutoff) {
                readingBuffer.removeFirst()
            }

            // ── Step 3: Temporal analysis over current window ─────────────
            val window = MotionWindow(readings = readingBuffer.toList())
            val temporalResult = temporalAnalyzer.analyze(window)

            val newTemporalEvent = if (temporalResult.eventType ==
                TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE
            ) {
                temporalResult
            } else {
                null
            }

            // ── Step 4: Confidence scoring ────────────────────────────────
            var accBreaches = 0
            var gyroBreaches = 0
            for (r in window.readings) {
                if (r.accelerationMagnitude >= config.accelerationThresholdMs2) {
                    accBreaches++
                }
                if (r.gyroscopeMagnitude >= config.gyroscopeThresholdRads) {
                    gyroBreaches++
                }
            }

            val scoringInput = ConfidenceScoringInput(
                temporalEvent = temporalResult,
                peakAccelerationMagnitude = temporalResult.peakAccelerationMagnitude,
                peakGyroscopeMagnitude = temporalResult.peakGyroscopeMagnitude,
                accelerationBreachCount = accBreaches,
                gyroscopeBreachCount = gyroBreaches,
                windowReadingCount = temporalResult.readingCount
            )
            val confidenceScore = confidenceScorer.score(scoringInput)

            val newCandidateEvent = if (newTemporalEvent != null) {
                CandidateAccidentEvent(
                    windowStartMs = temporalResult.windowStartMs,
                    windowEndMs = temporalResult.windowEndMs,
                    confidenceScore = confidenceScore,
                    temporalEvent = newTemporalEvent,
                    readingCount = temporalResult.readingCount
                )
            } else {
                null
            }

            // ── Step 5: Incident qualification ───────────────────────────
            val qualifiedIncident = qualificationEngine.process(newCandidateEvent)
            val candidateIdentity = qualifiedIncident?.let { "${it.windowStartMs}_${it.windowEndMs}" }
            val isNewIncident = candidateIdentity != null &&
                candidateIdentity != activeIncidentIdentity &&
                candidateIdentity != closedIncidentIdentity

            if (isNewIncident) {
                activeIncidentIdentity = candidateIdentity
                postImpactBuffer.clear()
                countdownEngine.reset()
                actionOrchestrator.reset()
                userDismissedEmergencyMode = false
            }

            val currentQualifiedIncident = if (isNewIncident) {
                qualifiedIncident
            } else if (activeIncidentIdentity != null) {
                _state.value.latestQualifiedIncident
            } else {
                null
            }
            if (currentQualifiedIncident != null) lifecycleQualifiedIncident = currentQualifiedIncident

            // ── Step 6: Post-impact motion observation ───────────────────
            val postImpactResult = if (currentQualifiedIncident != null) {
                if (reading.timestamp > currentQualifiedIncident.windowEndMs) {
                    postImpactBuffer.addLast(reading)
                    val windowLimit = currentQualifiedIncident.windowEndMs + postImpactConfig.postImpactWindowMillis
                    while (postImpactBuffer.isNotEmpty() && postImpactBuffer.first().timestamp > windowLimit) {
                        postImpactBuffer.removeFirst()
                    }
                }
                val postImpactWindow = PostImpactMotionWindow(readings = postImpactBuffer.toList())
                postImpactAnalyzer.analyze(currentQualifiedIncident, postImpactWindow)
            } else {
                null
            }

            // ── Step 7: Emergency decision ───────────────────────────────
            val decision = if (countdownEngine.state == EmergencyCountdownState.COUNTING_DOWN ||
                countdownEngine.state == EmergencyCountdownState.EXPIRED
            ) {
                _state.value.latestEmergencyDecision ?: decisionEngine.evaluate(currentQualifiedIncident, postImpactResult)
            } else {
                decisionEngine.evaluate(currentQualifiedIncident, postImpactResult)
            }

            // ── Step 8: Cancellation countdown ───────────────────────────
            var wasJustStarted = false
            if (activeIncidentIdentity == null || activeIncidentIdentity == qualified.identity) {
                decision.state == EmergencyDecisionState.EMERGENCY_CANDIDATE
            ) {
                if (countdownEngine.state == EmergencyCountdownState.IDLE) {
                    countdownEngine.start(reading.timestamp)
                    wasJustStarted = true
                } else if (countdownEngine.state == EmergencyCountdownState.COUNTING_DOWN) {
                    countdownEngine.update(reading.timestamp)
                }
            }

            if (wasJustStarted) {
                startCountdownTicker(reading.timestamp)
            }

            val isExpired = countdownEngine.state == EmergencyCountdownState.EXPIRED
            val isAlreadyTriggered = _state.value.latestEmergencyActionResult?.status == EmergencyActionResultStatus.TRIGGERED
            val shouldDispatch = isExpired &&
                !isDispatchInProgress &&
                !isAlreadyTriggered &&
                activeIncidentIdentity != null

            val dispatchTimestamp = if (shouldDispatch) {
                decision.incidentTimestampMs ?: reading.timestamp
            } else {
                null
            }
            val currentIncidentId = activeIncidentIdentity

            val dispatchPayload = if (activeIncidentIdentity != null) {
                _state.value.latestEmergencyDispatchPayload
            } else {
                null
            }

            val actionResult = if (_state.value.latestEmergencyActionResult?.status == EmergencyActionResultStatus.TRIGGERED ||
                _state.value.latestEmergencyActionResult?.status == EmergencyActionResultStatus.FAILED
            ) {
                _state.value.latestEmergencyActionResult
            } else if (countdownEngine.state == EmergencyCountdownState.EXPIRED) {
                null
            } else {
                actionOrchestrator.evaluate(
                    decision = decision,
                    countdownState = countdownEngine.state,
                    dispatchPayload = dispatchPayload
                )
            }

            updatePipelineDetectionState(
                reading = reading,
                motionEvent = motionEvent,
                newTemporalEvent = newTemporalEvent,
                newCandidateEvent = newCandidateEvent,
                currentQualifiedIncident = currentQualifiedIncident,
                postImpactResult = postImpactResult,
                decision = decision,
                actionResult = actionResult,
                isNewIncident = isNewIncident
            )


            if (dispatchTimestamp != null) {
                Pair(dispatchTimestamp, currentIncidentId)
            } else {
                null
            }
        }

        if (dispatchTrigger != null) {
            triggerEmergencyDispatch(dispatchTrigger.first, dispatchTrigger.second)
        }
    }

    private fun startCountdownTicker(startReadingTimestamp: Long) {
        countdownTickerJob?.cancel()
        val scope = activeScope ?: return
        val incidentId = activeIncidentIdentity
        val lifecycleRepository = emergencyLifecycleRepository
        if (lifecycleRepository != null && activeLifecycleIncidentId == null) {
            activeLifecycleIncidentId = UUID.randomUUID().toString()
            pendingLifecycleRecord = newLifecycleRecord(EmergencyLifecyclePhase.COUNTING_DOWN, EmergencyUserResponse.NONE)
            _state.update { it.copy(emergencyIncidentId = activeLifecycleIncidentId) }
        }
        val lifecycleRecord = pendingLifecycleRecord

        countdownTickerJob = scope.launch(countdownDispatcher) {
            if (lifecycleRepository != null && lifecycleRecord != null) {
                lifecycleMutex.withLock {
                    try { lifecycleRepository.createIfMissing(lifecycleRecord) } catch (_: Exception) { }
                }
            }
            val startWallClock = timeProvider()
            val tickInterval = 250L

            while (isActive) {
                delay(tickInterval)
                val wallElapsed = (timeProvider() - startWallClock).coerceAtLeast(0L)
                val currentTimestamp = startReadingTimestamp + wallElapsed

                var shouldDispatch = false
                var dispatchTimestamp = 0L
                var shouldContinue = true

                synchronized(this@CrashDetectionPipeline) {
                    if (activeIncidentIdentity != incidentId ||
                        countdownEngine.state != EmergencyCountdownState.COUNTING_DOWN
                    ) {
                        shouldContinue = false
                        return@synchronized
                    }

                    countdownEngine.update(currentTimestamp)
                    val remaining = countdownEngine.remainingMillis
                    val isExpired = countdownEngine.state == EmergencyCountdownState.EXPIRED

                    _state.update {
                        it.copy(
                            emergencyCountdownState = countdownEngine.state,
                            emergencyCountdownRemainingMillis = remaining,
                            emergencyDispatchUiState = if (isExpired) {
                                EmergencyDispatchUiState.DISPATCHING
                            } else {
                                EmergencyDispatchUiState.COUNTING_DOWN
                            }
                        )
                    }

                    if (isExpired) {
                        shouldContinue = false
                        val isAlreadyTriggered = _state.value.latestEmergencyActionResult?.status == EmergencyActionResultStatus.TRIGGERED
                        if (!isDispatchInProgress && !isAlreadyTriggered) {
                            shouldDispatch = true
                            dispatchTimestamp = _state.value.latestEmergencyDecision?.incidentTimestampMs ?: startReadingTimestamp
                        }
                    }
                }

                if (shouldDispatch) {
                    triggerEmergencyDispatch(dispatchTimestamp, incidentId)
                }

                if (!shouldContinue) {
                    break
                }
            }
        }
    }

    private fun triggerEmergencyDispatch(incidentTimestamp: Long, incidentId: String?) {
        synchronized(this) {
            if (isDispatchInProgress || activeIncidentIdentity != incidentId || activeIncidentIdentity == null) {
                return
            }
            isDispatchInProgress = true
        }

        val scope = activeScope ?: run {
            synchronized(this) { isDispatchInProgress = false }
            return
        }

        dispatchJob = scope.launch {
            val queriedLocation = try {
                withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    locationProvider?.getCurrentLocation()
                } ?: EmergencyLocation.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EmergencyLocation.Unavailable
            }
            val lifecycleId = activeLifecycleIncidentId
            val location = if (queriedLocation is EmergencyLocation.Available && isAcceptableEmergencyLocation(queriedLocation)) queriedLocation else {
                val cached = if (lifecycleId != null) {
                    try { emergencyLifecycleRepository?.get(lifecycleId) } catch (_: Exception) { null }
                } else null
                val timestamp = cached?.lastLocationTimestampEpochMillis
                val age = timestamp?.let { emergencyLifecycleClock.wallClockMillis() - it }
                if (cached?.lastLatitude != null && cached.lastLongitude != null && age != null && age in 0L..MAX_LAST_KNOWN_LOCATION_AGE_MS) {
                    EmergencyLocation.Available(
                        cached.lastLatitude, cached.lastLongitude, timestampMs = timestamp,
                        source = com.manish.vahakvigil.domain.model.EmergencyLocationSource.LAST_KNOWN
                    )
                } else EmergencyLocation.Unavailable
            }

            val payload = try {
                val compiler = dispatchDataCompiler
                if (compiler != null) {
                    val timestamp = if (incidentTimestamp != 0L) {
                        incidentTimestamp
                    } else {
                        _state.value.latestEmergencyDecision?.incidentTimestampMs ?: 0L
                    }
                    compiler.compile(timestamp, location = location)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

            val smsMessage = payload?.let {
                try { smsFormatter.format(it) } catch (_: Exception) { null }
            }

            val lifecycleRepository = emergencyLifecycleRepository
            var attemptClaimed = false
            if (lifecycleRepository != null && lifecycleId != null) {
                val record = pendingLifecycleRecord ?: newLifecycleRecord(EmergencyLifecyclePhase.DISPATCHING, if (_state.value.userAcknowledgedNeedHelp) EmergencyUserResponse.NEED_HELP else EmergencyUserResponse.NONE)
                attemptClaimed = lifecycleMutex.withLock {
                    try {
                        if (record != null) lifecycleRepository.createIfMissing(record)
                        if (location is EmergencyLocation.Available) {
                            lifecycleRepository.updateLocation(lifecycleId, location.latitude, location.longitude, location.timestampMs)
                        }
                        lifecycleRepository.beginDispatchAttempt(lifecycleId, allowFailedRetry = false, attemptedAtEpochMillis = emergencyLifecycleClock.wallClockMillis())
                    } catch (_: Exception) { false }
                }
                if (!attemptClaimed) {
                    synchronized(this@CrashDetectionPipeline) { isDispatchInProgress = false }
                    return@launch
                }
            }

            val actionResult = synchronized(this@CrashDetectionPipeline) {
                isDispatchInProgress = false
                if (readingsJob?.isActive != true && countdownTickerJob?.isActive != true && activeScope?.isActive != true) return@synchronized null
                if (activeIncidentIdentity != incidentId || activeIncidentIdentity == null) {
                    return@synchronized null
                }
                if (countdownEngine.state != EmergencyCountdownState.EXPIRED) {
                    return@synchronized null
                }

                val currentDecision = _state.value.latestEmergencyDecision
                val actionResult = actionOrchestrator.evaluate(
                    decision = currentDecision,
                    countdownState = countdownEngine.state,
                    dispatchPayload = payload
                )
                val dispatchUiState = when (actionResult.status) {
                    EmergencyActionResultStatus.TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
                    EmergencyActionResultStatus.FAILED -> if (actionResult.deliveryStatus == com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN) EmergencyDispatchUiState.DISPATCH_UNKNOWN else EmergencyDispatchUiState.DISPATCH_FAILED
                    EmergencyActionResultStatus.NOT_TRIGGERED -> EmergencyDispatchUiState.DISPATCHING
                }
                _state.update {
                    it.copy(
                        latestEmergencyDispatchPayload = payload,
                        latestEmergencySmsMessage = smsMessage,
                        latestEmergencyActionResult = actionResult,
                        emergencyDispatchUiState = dispatchUiState,
                        emergencyIncidentId = lifecycleId ?: it.emergencyIncidentId
                    )
                }
                actionResult
            }
            if (lifecycleRepository != null && lifecycleId != null && attemptClaimed) {
                val resolvedAt = emergencyLifecycleClock.wallClockMillis()
                val (phase, state) = when {
                    actionResult == null && _state.value.emergencyDispatchUiState == EmergencyDispatchUiState.CANCELLED -> EmergencyLifecyclePhase.CANCELLED to EmergencyDispatchAttemptState.FAILED
                    actionResult == null -> EmergencyLifecyclePhase.DISPATCH_FAILED to EmergencyDispatchAttemptState.FAILED
                    actionResult.status == EmergencyActionResultStatus.TRIGGERED -> EmergencyLifecyclePhase.DISPATCH_TRIGGERED to EmergencyDispatchAttemptState.SENT
                    actionResult.deliveryStatus == com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN -> EmergencyLifecyclePhase.DISPATCH_UNKNOWN to EmergencyDispatchAttemptState.UNKNOWN
                    else -> EmergencyLifecyclePhase.DISPATCH_FAILED to EmergencyDispatchAttemptState.FAILED
                }
                try { lifecycleRepository.updateOutcome(lifecycleId, phase, state, resolvedAt) } catch (_: Exception) { }
            }
        }
    }

    /** Persist only valid live ride fixes while an emergency countdown is active. */
    fun recordEmergencyLocation(location: com.manish.vahakvigil.domain.model.RideLocationState.Available) {
        if (!com.manish.vahakvigil.domain.model.EmergencyLocation.isValidCoordinate(location.latitude, location.longitude)) return
        val repository = emergencyLifecycleRepository ?: return
        val id = activeLifecycleIncidentId ?: return
        activeScope?.launch {
            lifecycleMutex.withLock {
                try {
                    pendingLifecycleRecord?.let { repository.createIfMissing(it) }
                    repository.updateLocation(id, location.latitude, location.longitude, location.timestampMs)
                } catch (_: Exception) { }
            }
        }
    }

    private fun isAcceptableEmergencyLocation(location: EmergencyLocation.Available): Boolean {
        if (location.source != com.manish.vahakvigil.domain.model.EmergencyLocationSource.LAST_KNOWN) return true
        val age = location.timestampMs?.let { emergencyLifecycleClock.wallClockMillis() - it } ?: return false
        return age in 0L..MAX_LAST_KNOWN_LOCATION_AGE_MS
    }

    private fun updatePipelineDetectionState(
        reading: SensorReading,
        motionEvent: com.manish.vahakvigil.domain.model.MotionEvent?,
        newTemporalEvent: com.manish.vahakvigil.domain.model.TemporalMotionEvent?,
        newCandidateEvent: CandidateAccidentEvent?,
        currentQualifiedIncident: com.manish.vahakvigil.domain.model.QualifiedIncident?,
        postImpactResult: com.manish.vahakvigil.domain.model.PostImpactMotionResult?,
        decision: com.manish.vahakvigil.domain.model.EmergencyDecision,
        actionResult: EmergencyActionResult?,
        isNewIncident: Boolean
    ) {
        val dispatchUiState = if (activeIncidentIdentity != null) {
            when {
                countdownEngine.state == EmergencyCountdownState.CANCELLED -> EmergencyDispatchUiState.CANCELLED
                countdownEngine.state == EmergencyCountdownState.COUNTING_DOWN -> EmergencyDispatchUiState.COUNTING_DOWN
                countdownEngine.state == EmergencyCountdownState.EXPIRED -> {
                    when (actionResult?.status) {
                        EmergencyActionResultStatus.TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
                    EmergencyActionResultStatus.FAILED -> if (actionResult.deliveryStatus == com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN) EmergencyDispatchUiState.DISPATCH_UNKNOWN else EmergencyDispatchUiState.DISPATCH_FAILED
                        EmergencyActionResultStatus.NOT_TRIGGERED -> EmergencyDispatchUiState.DISPATCHING
                        null -> EmergencyDispatchUiState.DISPATCHING
                    }
                }
                else -> EmergencyDispatchUiState.IDLE
            }
        } else {
            _state.value.emergencyDispatchUiState
        }

        val isEmergencyActive = when (countdownEngine.state) {
            EmergencyCountdownState.COUNTING_DOWN -> true
            EmergencyCountdownState.EXPIRED -> !userDismissedEmergencyMode
            EmergencyCountdownState.CANCELLED,
            EmergencyCountdownState.IDLE -> false
        }

        val effectiveCountdownState = if (activeIncidentIdentity == null && _state.value.emergencyCountdownState == EmergencyCountdownState.CANCELLED) {
            EmergencyCountdownState.CANCELLED
        } else {
            countdownEngine.state
        }

        val effectiveRemainingMillis = if (activeIncidentIdentity == null && effectiveCountdownState != EmergencyCountdownState.COUNTING_DOWN) {
            decisionConfig.countdownDurationMillis
        } else {
            countdownEngine.remainingMillis
        }

        _state.update {
            it.copy(
                latestReading = reading,
                latestMotionEvent = motionEvent,
                latestTemporalEvent = newTemporalEvent,
                latestCandidateAccidentEvent = newCandidateEvent,
                latestQualifiedIncident = currentQualifiedIncident,
                latestPostImpactResult = postImpactResult ?: if (activeIncidentIdentity == null) null else it.latestPostImpactResult,
                latestEmergencyDecision = decision,
                emergencyCountdownState = effectiveCountdownState,
                emergencyCountdownRemainingMillis = effectiveRemainingMillis,
                latestEmergencyActionResult = actionResult,
                isEmergencyModeActive = isEmergencyActive,
                emergencyDispatchUiState = dispatchUiState,
                userAcknowledgedNeedHelp = if (isNewIncident) false else it.userAcknowledgedNeedHelp
            )
        }
    }

    @Synchronized
    fun acknowledgeNeedHelp() {
        persistLifecyclePhase(EmergencyLifecyclePhase.COUNTING_DOWN, EmergencyUserResponse.NEED_HELP)
        _state.update {
            it.copy(userAcknowledgedNeedHelp = true)
        }
    }

    /**
     * Immediately confirms emergency distress (e.g. via deliberate Slide-to-Confirm),
     * safely cancelling any running countdown ticker and executing the exact same
     * emergency escalation workflow that occurs at 75-second countdown expiry.
     *
     * Guards:
     * - Must have an active candidate incident ([activeIncidentIdentity] != null).
     * - Countdown must currently be in [EmergencyCountdownState.COUNTING_DOWN].
     * - Prevents duplicate escalation if dispatch is already in progress or completed.
     */
    @Synchronized
    fun confirmEmergencyImmediately() {
        val incidentId = activeIncidentIdentity ?: return
        if (countdownEngine.state != EmergencyCountdownState.COUNTING_DOWN) return
        val isAlreadyTriggered = _state.value.latestEmergencyActionResult?.status == EmergencyActionResultStatus.TRIGGERED
        if (isDispatchInProgress || isAlreadyTriggered) return

        // 1. Cancel countdown ticker immediately to prevent future ticks/expiry
        countdownTickerJob?.cancel()
        countdownTickerJob = null

        // 2. Immediately transition countdown engine to EXPIRED with 0ms remaining
        countdownEngine.expireImmediately()
        persistLifecyclePhase(EmergencyLifecyclePhase.DISPATCHING, EmergencyUserResponse.NEED_HELP)

        // 3. Capture incident timestamp for dispatch payload compilation
        val dispatchTimestamp = _state.value.latestEmergencyDecision?.incidentTimestampMs ?: timeProvider()

        // 4. Update pipeline state to reflect immediate expiration and dispatching
        _state.update {
            it.copy(
                emergencyCountdownState = EmergencyCountdownState.EXPIRED,
                emergencyCountdownRemainingMillis = 0L,
                emergencyDispatchUiState = EmergencyDispatchUiState.DISPATCHING,
                userAcknowledgedNeedHelp = true
            )
        }

        // 5. Trigger the exact same emergency escalation path
        triggerEmergencyDispatch(dispatchTimestamp, incidentId)
    }

    @Synchronized
    fun cancelEmergencyCountdown() {
        if (countdownEngine.state == EmergencyCountdownState.EXPIRED &&
            _state.value.latestEmergencyActionResult?.status == EmergencyActionResultStatus.TRIGGERED
        ) {
            return
        }
        val wasActive = countdownEngine.state == EmergencyCountdownState.COUNTING_DOWN ||
            countdownEngine.state == EmergencyCountdownState.EXPIRED
        if (wasActive) persistLifecyclePhase(EmergencyLifecyclePhase.CANCELLED, EmergencyUserResponse.IM_FINE)
        closedIncidentIdentity = activeIncidentIdentity
        activeIncidentIdentity = null

        countdownTickerJob?.cancel()
        countdownTickerJob = null
        dispatchJob?.cancel()
        dispatchJob = null
        isDispatchInProgress = false

        countdownEngine.cancel()
        val actionResult = actionOrchestrator.evaluate(
            decision = _state.value.latestEmergencyDecision,
            countdownState = if (wasActive) EmergencyCountdownState.CANCELLED else countdownEngine.state,
            dispatchPayload = _state.value.latestEmergencyDispatchPayload
        )
        readingBuffer.clear()
        postImpactBuffer.clear()
        qualificationEngine.reset()
        countdownEngine.reset()
        userDismissedEmergencyMode = false
        activeLifecycleIncidentId = null
        pendingLifecycleRecord = null
        lifecycleQualifiedIncident = null

        _state.update {
            it.copy(
                latestQualifiedIncident = null,
                latestEmergencyDecision = null,
                latestEmergencyDispatchPayload = null,
                latestEmergencySmsMessage = null,
                emergencyCountdownState = if (wasActive) EmergencyCountdownState.CANCELLED else EmergencyCountdownState.IDLE,
                emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis,
                latestEmergencyActionResult = actionResult,
                isEmergencyModeActive = false,
                userAcknowledgedNeedHelp = false,
                emergencyDispatchUiState = if (wasActive) EmergencyDispatchUiState.CANCELLED else EmergencyDispatchUiState.IDLE
            )
        }
    }

    @Synchronized
    fun dismissEmergencyMode() {
        persistLifecyclePhase(EmergencyLifecyclePhase.COMPLETED, if (_state.value.userAcknowledgedNeedHelp) EmergencyUserResponse.NEED_HELP else EmergencyUserResponse.NONE)
        closedIncidentIdentity = activeIncidentIdentity
        activeIncidentIdentity = null
        userDismissedEmergencyMode = true

        countdownTickerJob?.cancel()
        countdownTickerJob = null
        dispatchJob?.cancel()
        dispatchJob = null
        isDispatchInProgress = false

        countdownEngine.reset()
        readingBuffer.clear()
        postImpactBuffer.clear()
        qualificationEngine.reset()
        actionOrchestrator.reset()
        activeLifecycleIncidentId = null
        pendingLifecycleRecord = null
        lifecycleQualifiedIncident = null

        _state.update {
            it.copy(
                latestQualifiedIncident = null,
                latestEmergencyDecision = null,
                latestEmergencyDispatchPayload = null,
                latestEmergencySmsMessage = null,
                isEmergencyModeActive = false,
                emergencyCountdownState = EmergencyCountdownState.IDLE,
                emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis,
                emergencyDispatchUiState = EmergencyDispatchUiState.IDLE,
                userAcknowledgedNeedHelp = false
            )
        }
    }

    @Synchronized
    fun retryEmergencyDispatch() {
        val decision = _state.value.latestEmergencyDecision
        val payload = _state.value.latestEmergencyDispatchPayload
        if (decision?.state == EmergencyDecisionState.EMERGENCY_CANDIDATE &&
            countdownEngine.state == EmergencyCountdownState.EXPIRED
        ) {
            val lifecycleRepository = emergencyLifecycleRepository
            val lifecycleId = activeLifecycleIncidentId
            val scope = activeScope
            if (lifecycleRepository != null && (lifecycleId == null || scope == null)) return
            if (lifecycleRepository != null && lifecycleId != null && scope != null) {
                if (_state.value.emergencyDispatchUiState != EmergencyDispatchUiState.DISPATCH_FAILED) return
                if (isDispatchInProgress) return
                isDispatchInProgress = true
                scope.launch {
                    val claimed = lifecycleMutex.withLock {
                        try {
                            pendingLifecycleRecord?.let { lifecycleRepository.createIfMissing(it) }
                            lifecycleRepository.beginDispatchAttempt(lifecycleId, allowFailedRetry = true, attemptedAtEpochMillis = emergencyLifecycleClock.wallClockMillis())
                        } catch (_: Exception) { false }
                    }
                    if (!claimed) {
                        synchronized(this@CrashDetectionPipeline) { isDispatchInProgress = false }
                        return@launch
                    }
                    val result = actionOrchestrator.evaluate(
                        decision = decision,
                        countdownState = EmergencyCountdownState.EXPIRED,
                        dispatchPayload = payload
                    )
                    val (phase, state) = when {
                        result.status == EmergencyActionResultStatus.TRIGGERED -> EmergencyLifecyclePhase.DISPATCH_TRIGGERED to EmergencyDispatchAttemptState.SENT
                        result.deliveryStatus == com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN -> EmergencyLifecyclePhase.DISPATCH_UNKNOWN to EmergencyDispatchAttemptState.UNKNOWN
                        else -> EmergencyLifecyclePhase.DISPATCH_FAILED to EmergencyDispatchAttemptState.FAILED
                    }
                    try { lifecycleRepository.updateOutcome(lifecycleId, phase, state, emergencyLifecycleClock.wallClockMillis()) } catch (_: Exception) { }
                    synchronized(this@CrashDetectionPipeline) {
                        isDispatchInProgress = false
                        _state.update {
                            it.copy(
                                latestEmergencyActionResult = result,
                                emergencyDispatchUiState = when (phase) {
                                    EmergencyLifecyclePhase.DISPATCH_TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
                                    EmergencyLifecyclePhase.DISPATCH_FAILED -> EmergencyDispatchUiState.DISPATCH_FAILED
                                    else -> EmergencyDispatchUiState.DISPATCH_UNKNOWN
                                }
                            )
                        }
                    }
                }
                return
            }
            val result = actionOrchestrator.evaluate(
                decision = decision,
                countdownState = EmergencyCountdownState.EXPIRED,
                dispatchPayload = payload
            )
            val newDispatchUiState = when (result.status) {
                EmergencyActionResultStatus.TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
                EmergencyActionResultStatus.FAILED -> if (result.deliveryStatus == com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN) EmergencyDispatchUiState.DISPATCH_UNKNOWN else EmergencyDispatchUiState.DISPATCH_FAILED
                EmergencyActionResultStatus.NOT_TRIGGERED -> EmergencyDispatchUiState.DISPATCHING
            }
            _state.update {
                it.copy(
                    latestEmergencyActionResult = result,
                    emergencyDispatchUiState = newDispatchUiState
                )
            }
        }
    }

    suspend fun prepareDispatchPayload(incidentTimestamp: Long = 0L): EmergencyDispatchPayload? {
        val location = try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                locationProvider?.getCurrentLocation()
            } ?: EmergencyLocation.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            EmergencyLocation.Unavailable
        }
        val compiler = dispatchDataCompiler ?: return null
        val timestamp = if (incidentTimestamp != 0L) {
            incidentTimestamp
        } else {
            _state.value.latestEmergencyDecision?.incidentTimestampMs ?: 0L
        }
        val payload = compiler.compile(timestamp, location = location)
        val smsMessage = smsFormatter.format(payload)
        _state.update {
            it.copy(
                latestEmergencyDispatchPayload = payload,
                latestEmergencySmsMessage = smsMessage
            )
        }
        return payload
    }

    companion object {
        const val LOCATION_TIMEOUT_MS = 5_000L
        const val MAX_LAST_KNOWN_LOCATION_AGE_MS = 15 * 60_000L
    }
}
