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
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionWindow
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionWindow
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.repository.SensorRepository
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
    private val countdownDispatcher: CoroutineDispatcher = Dispatchers.Default
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
    @Volatile private var isDispatchInProgress: Boolean = false

    val isRunning: Boolean
        get() = readingsJob?.isActive == true

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

        val hasActiveEmergency = _state.value.isEmergencyModeActive || activeIncidentIdentity != null
        if (!hasActiveEmergency) {
            readingBuffer.clear()
            postImpactBuffer.clear()
            qualificationEngine.reset()
            countdownEngine.reset()
            actionOrchestrator.reset()
            activeIncidentIdentity = null
            closedIncidentIdentity = null
            userDismissedEmergencyMode = false
        } else if (countdownEngine.state == EmergencyCountdownState.COUNTING_DOWN) {
            val startTs = countdownEngine.startTimestampMillis ?: timeProvider()
            startCountdownTicker(startTs)
        }

        sensorRepository.startMonitoring()

        readingsJob = coroutineScope.launch {
            sensorRepository.sensorReadings.collect { reading ->
                processReading(reading)
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
            if (decision.state == EmergencyDecisionState.EMERGENCY_CANDIDATE) {
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

        countdownTickerJob = scope.launch(countdownDispatcher) {
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
            val location = try {
                withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    locationProvider?.getCurrentLocation()
                } ?: EmergencyLocation.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                EmergencyLocation.Unavailable
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

            synchronized(this@CrashDetectionPipeline) {
                isDispatchInProgress = false
                if (readingsJob?.isActive != true && countdownTickerJob?.isActive != true && activeScope?.isActive != true) return@synchronized
                if (activeIncidentIdentity != incidentId || activeIncidentIdentity == null) {
                    return@synchronized
                }
                if (countdownEngine.state != EmergencyCountdownState.EXPIRED) {
                    return@synchronized
                }

                val currentDecision = _state.value.latestEmergencyDecision
                val actionResult = actionOrchestrator.evaluate(
                    decision = currentDecision,
                    countdownState = countdownEngine.state,
                    dispatchPayload = payload
                )
                val dispatchUiState = when (actionResult.status) {
                    EmergencyActionResultStatus.TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
                    EmergencyActionResultStatus.FAILED -> EmergencyDispatchUiState.DISPATCH_FAILED
                    EmergencyActionResultStatus.NOT_TRIGGERED -> EmergencyDispatchUiState.DISPATCHING
                }
                _state.update {
                    it.copy(
                        latestEmergencyDispatchPayload = payload,
                        latestEmergencySmsMessage = smsMessage,
                        latestEmergencyActionResult = actionResult,
                        emergencyDispatchUiState = dispatchUiState
                    )
                }
            }
        }
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
                        EmergencyActionResultStatus.FAILED -> EmergencyDispatchUiState.DISPATCH_FAILED
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
            val result = actionOrchestrator.evaluate(
                decision = decision,
                countdownState = EmergencyCountdownState.EXPIRED,
                dispatchPayload = payload
            )
            val newDispatchUiState = when (result.status) {
                EmergencyActionResultStatus.TRIGGERED -> EmergencyDispatchUiState.DISPATCH_TRIGGERED
                EmergencyActionResultStatus.FAILED -> EmergencyDispatchUiState.DISPATCH_FAILED
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
    }
}
