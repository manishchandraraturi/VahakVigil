package com.manish.vahakvigil.ui.screens.home

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.analyzer.ConfidenceScorer
import com.manish.vahakvigil.domain.analyzer.EmergencyCountdownEngine
import com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine
import com.manish.vahakvigil.domain.analyzer.EventQualificationEngine
import com.manish.vahakvigil.domain.analyzer.MotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer
import com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.location.RidePathTracker
import com.manish.vahakvigil.domain.location.RidePlanningCoordinator
import com.manish.vahakvigil.domain.repository.EmptyDestinationSearchRepository
import com.manish.vahakvigil.domain.repository.UnconfiguredRideRoutingRepository
import com.manish.vahakvigil.domain.model.RideDestination
import com.manish.vahakvigil.domain.model.ConfidenceScoringConfig
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyDispatchUiState
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionEventType
import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.orchestration.SmsEmergencyActionExecutor
import com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline
import com.manish.vahakvigil.domain.repository.ContactRepository
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository
import com.manish.vahakvigil.domain.repository.SensorRepository
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.EmergencyIncidentRepository
import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.sms.EmergencySmsSender
import com.manish.vahakvigil.service.SafetyMonitoringService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel for the Home screen monitoring state.
 *
 * Connects the UI to [CrashDetectionPipeline] and [SensorRepository] to observe
 * ride monitoring and crash detection state independent of Activity recreation.
 */
class HomeViewModel(
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
    private val timezoneManager: com.manish.vahakvigil.data.preferences.TimezoneManager? = null,
    private val ridePlanningCoordinator: RidePlanningCoordinator = RidePlanningCoordinator(
        EmptyDestinationSearchRepository(),
        UnconfiguredRideRoutingRepository()
    ),
    private val rideSessionRepository: RideSessionRepository? = null,
    private val emergencyIncidentRepository: EmergencyIncidentRepository? = null,
    val pipeline: CrashDetectionPipeline = CrashDetectionPipeline(
        sensorRepository = sensorRepository,
        config = config,
        scoringConfig = scoringConfig,
        qualificationConfig = qualificationConfig,
        postImpactConfig = postImpactConfig,
        decisionConfig = decisionConfig,
        motionAnalyzer = motionAnalyzer,
        temporalAnalyzer = temporalAnalyzer,
        confidenceScorer = confidenceScorer,
        qualificationEngine = qualificationEngine,
        postImpactAnalyzer = postImpactAnalyzer,
        decisionEngine = decisionEngine,
        countdownEngine = countdownEngine,
        actionOrchestrator = actionOrchestrator,
        dispatchDataCompiler = dispatchDataCompiler,
        smsFormatter = smsFormatter,
        locationProvider = locationProvider
    )
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            capabilities = sensorRepository.capabilities,
            emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val ridePathTracker: RidePathTracker = RidePathTracker()
    private var routeLoadJob: Job? = null
    private val ridePathMutex = Mutex()

    private var activeZoneId: java.time.ZoneId = com.manish.vahakvigil.data.preferences.TimezoneManager.resolveZoneId(null)
    private var lastScheduledIncident: EmergencyIncidentRecord? = null
    private var motionHoldJob: Job? = null
    private var lastToggleTimestamp: Long = 0L

    private fun updateDateTimeState(zoneId: java.time.ZoneId = activeZoneId) {
        activeZoneId = zoneId
        val now = java.time.Instant.now()
        val dateFormatted = com.manish.vahakvigil.data.preferences.TimezoneManager.formatDate(now, zoneId)
        val timeFormatted = com.manish.vahakvigil.data.preferences.TimezoneManager.formatTime(now, zoneId)
        val (regionName, offsetStr) = com.manish.vahakvigil.data.preferences.TimezoneManager.formatRegionDetails(now, zoneId)
        val greeting = com.manish.vahakvigil.data.preferences.TimezoneManager.formatGreeting(now, zoneId)

        _uiState.update {
            it.copy(
                currentDateFormatted = dateFormatted,
                currentTimeFormatted = timeFormatted,
                timeRegionFormatted = regionName,
                timezoneOffsetFormatted = offsetStr,
                greetingFormatted = greeting
            )
        }
    }

    fun refreshDateTime() {
        updateDateTimeState()
    }

    init {
        updateDateTimeState()

        viewModelScope.launch {
            ridePlanningCoordinator.state.collect { planningState ->
                _uiState.update { it.copy(ridePlanning = planningState) }
            }
        }

        if (timezoneManager != null) {
            viewModelScope.launch {
                timezoneManager.selectedZoneId.collect { zoneIdString ->
                    val zoneId = com.manish.vahakvigil.data.preferences.TimezoneManager.resolveZoneId(zoneIdString)
                    updateDateTimeState(zoneId)
                }
            }
        }

        // Observe CrashDetectionPipeline state and update HomeUiState
        viewModelScope.launch {
            pipeline.state.collect { detectionState ->
                val incident = detectionState.latestQualifiedIncident
                if (incident != null && emergencyIncidentRepository != null) {
                    val dispatchLocation = detectionState.latestEmergencyDispatchPayload?.location as? EmergencyLocation.Available
                    val confidence = incident.qualifyingConfidenceScore.value.coerceIn(0f, 1f)
                    val record = EmergencyIncidentRecord(
                        incidentId = incident.windowEndMs.toString(),
                        incidentTimestampEpochMillis = incident.windowEndMs,
                        windowStartEpochMillis = incident.windowStartMs,
                        windowEndEpochMillis = incident.windowEndMs,
                        latitude = dispatchLocation?.latitude,
                        longitude = dispatchLocation?.longitude,
                        confidenceScore = confidence,
                        evidenceSummary = "${incident.qualifyingWindowCount} qualified sensor windows · ${incident.candidateEvents.size} motion events",
                        status = detectionState.emergencyDispatchUiState.name,
                        userAction = when {
                            detectionState.userAcknowledgedNeedHelp -> "HELP_CONFIRMED"
                            detectionState.emergencyDispatchUiState == com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.CANCELLED -> "COUNTDOWN_CANCELLED"
                            else -> "NO_ACTION_RECORDED"
                        }
                    )
                    if (record != lastScheduledIncident) {
                        lastScheduledIncident = record
                        viewModelScope.launch {
                            try {
                                emergencyIncidentRepository.recordIncident(record)
                            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                // Incident history is best-effort and must not block emergency state propagation.
                            }
                        }
                    }
                }
                val newEvent = detectionState.latestMotionEvent
                if (newEvent?.eventType == MotionEventType.COMBINED_IMPACT) {
                    motionHoldJob?.cancel()
                    motionHoldJob = viewModelScope.launch {
                        delay(1500L)
                        _uiState.update { current ->
                            current.copy(
                                displayedMotionEvent = pipeline.state.value.latestMotionEvent
                            )
                        }
                        motionHoldJob = null
                    }
                }
                val currentDisplayed = if (motionHoldJob != null) {
                    _uiState.value.displayedMotionEvent ?: newEvent
                } else {
                    newEvent
                }

                _uiState.update { current ->
                    current.copy(
                        latestReading = detectionState.latestReading,
                        latestMotionEvent = detectionState.latestMotionEvent,
                        displayedMotionEvent = currentDisplayed,
                        latestTemporalEvent = detectionState.latestTemporalEvent,
                        latestCandidateAccidentEvent = detectionState.latestCandidateAccidentEvent,
                        latestQualifiedIncident = detectionState.latestQualifiedIncident,
                        latestPostImpactResult = detectionState.latestPostImpactResult,
                        latestEmergencyDecision = detectionState.latestEmergencyDecision,
                        emergencyCountdownState = detectionState.emergencyCountdownState,
                        emergencyCountdownRemainingMillis = detectionState.emergencyCountdownRemainingMillis,
                        latestEmergencyActionResult = detectionState.latestEmergencyActionResult,
                        latestEmergencyDispatchPayload = detectionState.latestEmergencyDispatchPayload,
                        latestEmergencySmsMessage = detectionState.latestEmergencySmsMessage,
                        isEmergencyModeActive = detectionState.isEmergencyModeActive,
                        emergencyDispatchUiState = detectionState.emergencyDispatchUiState,
                        emergencyIncidentId = detectionState.emergencyIncidentId,
                        userAcknowledgedNeedHelp = detectionState.userAcknowledgedNeedHelp
                    )
                }
            }
        }

        // Observe live ride location from SafetyMonitoringService
        viewModelScope.launch {
            SafetyMonitoringService.currentRideLocation.collect { locState ->
                var acceptedDistanceIncrement: Double? = null
                if (locState is RideLocationState.Available) {
                    if (_uiState.value.isMonitoring) {
                        ridePathMutex.withLock {
                            val previous = ridePathTracker.getCurrentPath().lastOrNull()
                            if (ridePathTracker.addFromState(locState) && previous != null) {
                                acceptedDistanceIncrement = RidePathTracker.calculateDistanceMeters(
                                    previous.latitude, previous.longitude, locState.latitude, locState.longitude
                                )
                            }
                        }
                    }
                }
                ridePlanningCoordinator.onLocationState(locState)?.let(::loadRideRoute)
                _uiState.update { current ->
                    current.copy(
                        rideLocationState = locState,
                        ridePath = if (locState is RideLocationState.Inactive) emptyList() else ridePathTracker.getCurrentPath(),
                        rideDistanceMeters = when {
                            locState is RideLocationState.Inactive -> null
                            acceptedDistanceIncrement != null -> (current.rideDistanceMeters ?: 0.0) + acceptedDistanceIncrement!!
                            current.ridePath.size < 2 && ridePathTracker.size() >= 2 -> current.rideDistanceMeters ?: 0.0
                            else -> current.rideDistanceMeters
                        }
                    )
                }
            }
        }

        // Observe repository monitoring state and handle UI state cleanup
        viewModelScope.launch {
            sensorRepository.isMonitoring.collect { isMonitoring ->
                _uiState.update {
                    it.copy(
                        isMonitoring = isMonitoring,
                        rideStartedAtEpochMillis = if (isMonitoring) it.rideStartedAtEpochMillis ?: System.currentTimeMillis() else null,
                        rideDistanceMeters = if (isMonitoring) it.rideDistanceMeters else null
                    )
                }
                if (isMonitoring) {
                    restoreActiveRidePath()
                    ridePlanningCoordinator.onRideStarted()?.let(::loadRideRoute)
                } else {
                    ridePathMutex.withLock { ridePathTracker.clear() }
                    routeLoadJob?.cancel()
                    routeLoadJob = null
                    ridePlanningCoordinator.onRideStopped()
                    stopObservingReadings()
                }
            }
        }
    }

    private suspend fun restoreActiveRidePath() {
        ridePathMutex.withLock {
            ridePathTracker.reset()
            val repository = rideSessionRepository ?: return
            val activeSession = try {
                repository.getActiveSession()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            } ?: return
            val savedPoints = try {
                repository.getPoints(activeSession.id)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emptyList()
            }
            savedPoints.forEach { point ->
                ridePathTracker.addPoint(
                    com.manish.vahakvigil.domain.model.RideLocationPoint(point.latitude, point.longitude, point.accuracyMeters, point.timestampEpochMillis),
                    currentTimeMs = point.timestampEpochMillis
                )
            }
            _uiState.update {
                it.copy(
                    rideStartedAtEpochMillis = activeSession.startedAtEpochMillis,
                    rideDistanceMeters = com.manish.vahakvigil.domain.location.RideRouteReplay.distanceMeters(savedPoints)
                )
            }
        }
    }

    private fun stopObservingReadings() {
        motionHoldJob?.cancel()
        motionHoldJob = null
        ridePathTracker.clear()
        val hasActiveEmergency = pipeline.state.value.isEmergencyModeActive
        if (!com.manish.vahakvigil.service.SafetyMonitoringService.isServiceRunning.value && !hasActiveEmergency) {
            pipeline.stop()
        }
        if (!hasActiveEmergency) {
            _uiState.update {
                it.copy(
                    latestReading = null,
                    latestMotionEvent = null,
                    displayedMotionEvent = null,
                    latestTemporalEvent = null,
                    latestCandidateAccidentEvent = null,
                    latestQualifiedIncident = null,
                    latestPostImpactResult = null,
                    latestEmergencyDecision = null,
                    emergencyCountdownState = EmergencyCountdownState.IDLE,
                    emergencyCountdownRemainingMillis = decisionConfig.countdownDurationMillis,
                    latestEmergencyActionResult = null,
                    latestEmergencyDispatchPayload = null,
                    latestEmergencySmsMessage = null,
                    isEmergencyModeActive = false,
                    userAcknowledgedNeedHelp = false,
                    emergencyDispatchUiState = EmergencyDispatchUiState.IDLE,
                    rideLocationState = RideLocationState.Inactive,
                    ridePath = emptyList(),
                    rideStartedAtEpochMillis = null,
                    rideDistanceMeters = null
                )
            }
        }
    }

    /**
     * Records the user's explicit acknowledgment that they need help during an emergency candidate state.
     */
    fun acknowledgeNeedHelp() {
        pipeline.acknowledgeNeedHelp()
        _uiState.update {
            it.copy(userAcknowledgedNeedHelp = true)
        }
    }

    /**
     * Confirms emergency distress immediately (e.g. via deliberate Slide-to-Confirm),
     * executing the exact same escalation workflow that occurs at 75-second countdown expiry.
     */
    fun confirmEmergencyImmediately() {
        pipeline.confirmEmergencyImmediately()
    }

    /**
     * Cancels the active emergency countdown upon user request before expiration.
     */
    fun cancelEmergencyCountdown() {
        if (pipeline.state.value.emergencyCountdownState == EmergencyCountdownState.EXPIRED) {
            return
        }
        motionHoldJob?.cancel()
        motionHoldJob = null
        pipeline.cancelEmergencyCountdown()
        val pipelineState = pipeline.state.value
        _uiState.update {
            it.copy(
                displayedMotionEvent = pipelineState.latestMotionEvent,
                emergencyCountdownState = pipelineState.emergencyCountdownState,
                emergencyCountdownRemainingMillis = pipelineState.emergencyCountdownRemainingMillis,
                latestEmergencyActionResult = pipelineState.latestEmergencyActionResult,
                isEmergencyModeActive = false,
                userAcknowledgedNeedHelp = false,
                emergencyDispatchUiState = EmergencyDispatchUiState.CANCELLED
            )
        }
    }

    /**
     * Dismisses the post-expiration emergency status display and safely returns the rider to the normal UI.
     */
    fun dismissEmergencyMode() {
        motionHoldJob?.cancel()
        motionHoldJob = null
        pipeline.dismissEmergencyMode()
        _uiState.update {
            it.copy(
                displayedMotionEvent = null,
                isEmergencyModeActive = false
            )
        }
    }

    /**
     * Retries emergency action dispatch via the orchestrator when a previous dispatch attempt failed.
     */
    fun retryEmergencyDispatch() {
        pipeline.retryEmergencyDispatch()
        val pipelineState = pipeline.state.value
        _uiState.update {
            it.copy(
                latestEmergencyActionResult = pipelineState.latestEmergencyActionResult,
                emergencyDispatchUiState = pipelineState.emergencyDispatchUiState
            )
        }
    }

    /**
     * Prepares an emergency dispatch payload using stored Room data via [EmergencyDispatchDataCompiler].
     */
    suspend fun prepareDispatchPayload(incidentTimestamp: Long = 0L): EmergencyDispatchPayload? {
        val payload = pipeline.prepareDispatchPayload(incidentTimestamp)
        val pipelineState = pipeline.state.value
        _uiState.update {
            it.copy(
                latestEmergencyDispatchPayload = payload,
                latestEmergencySmsMessage = pipelineState.latestEmergencySmsMessage
            )
        }
        return payload
    }

    /**
     * Toggles motion monitoring on or off.
     * When [context] is provided (Android UI), delegates directly to [SafetyMonitoringService]
     * which owns the background foreground-service lifecycle, [CrashDetectionPipeline], and sensors.
     * When [context] is null (e.g. unit tests), directly controls [pipeline].
     */
    fun toggleMonitoring(context: android.content.Context? = null) {
        if (context != null) {
            val now = System.currentTimeMillis()
            if (now - lastToggleTimestamp < 500L) {
                return
            }
            lastToggleTimestamp = now
        }

        if (_uiState.value.isMonitoring) {
            if (context != null) {
                com.manish.vahakvigil.service.SafetyMonitoringService.stopService(context)
            } else {
                pipeline.stop()
            }
        } else {
            if (context != null) {
                com.manish.vahakvigil.service.SafetyMonitoringService.startService(context)
            } else {
                pipeline.start(viewModelScope)
            }
        }
    }

    fun updateRideDestinationQuery(query: String) = ridePlanningCoordinator.setSearchQuery(query)

    fun searchRideDestinations(query: String = ridePlanningCoordinator.state.value.searchQuery) {
        ridePlanningCoordinator.setSearchQuery(query)
        viewModelScope.launch { ridePlanningCoordinator.searchDestinations(query) }
    }

    fun selectRideDestination(destination: RideDestination) {
        ridePlanningCoordinator.selectDestination(destination)?.let(::loadRideRoute)
    }

    fun clearRideDestination() {
        routeLoadJob?.cancel()
        routeLoadJob = null
        ridePlanningCoordinator.clearDestination()
    }

    private fun loadRideRoute(request: RidePlanningCoordinator.RouteRequest) {
        routeLoadJob?.cancel()
        routeLoadJob = viewModelScope.launch { ridePlanningCoordinator.loadRoute(request) }
    }

    /**
     * Evaluates permission results from the system permission launcher (HP-02).
     *
     * Behavior:
     * - If all requested permissions are granted, monitoring starts immediately.
     * - If SEND_SMS is denied, automated emergency SMS cannot function.
     *   Monitoring does NOT start automatically; user receives a clear explanation.
     * - If Location permission is denied, emergency dispatches cannot acquire GPS coordinates.
     *   The limitation is recorded honestly.
     * - If POST_NOTIFICATIONS is denied, sensor monitoring is NOT blocked, but
     *   the background notification limitation is recorded honestly.
     */
    fun onPermissionsResult(
        permissions: Map<String, Boolean>,
        context: Context? = null
    ) {
        val smsGranted = permissions[Manifest.permission.SEND_SMS] ?: true
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: true
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: true
        val locationGranted = fineLocation || coarseLocation
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS]
            ?: permissions["android.permission.POST_NOTIFICATIONS"]
            ?: true

        val limitations = mutableListOf<String>()
        if (!smsGranted) {
            limitations.add("Automated emergency SMS unavailable (SEND_SMS denied)")
        }
        if (!locationGranted) {
            limitations.add("Accurate emergency location unavailable (Location denied)")
        }
        if (!notificationGranted) {
            limitations.add("Background status notifications disabled")
        }

        val explanation = when {
            !smsGranted && !locationGranted ->
                "SMS and Location permissions are denied. VahakVigil cannot send emergency SMS alerts to your contacts or acquire your GPS location during a crash."
            !smsGranted ->
                "SMS permission is denied. VahakVigil cannot automatically dispatch emergency SMS alerts to your emergency contacts."
            !locationGranted ->
                "Location permission is denied. Emergency alerts will not include your GPS coordinates."
            !notificationGranted ->
                "Notification permission is denied. Ride safety monitoring will proceed, but alerts and countdown notifications will not appear in the status bar."
            else -> null
        }

        _uiState.update { current ->
            current.copy(
                permissionExplanation = explanation,
                permissionLimitations = limitations,
                isSmsPermissionGranted = smsGranted,
                isLocationPermissionGranted = locationGranted,
                isNotificationPermissionGranted = notificationGranted
            )
        }

        // Critical permission enforcement:
        // Do not automatically start monitoring if critical SEND_SMS is denied.
        // Location and notification denials do not unnecessarily block sensor monitoring.
        if (smsGranted) {
            toggleMonitoring(context)
        }
    }

    /**
     * Dismisses the current permission explanation dialog / notice.
     */
    fun dismissPermissionExplanation() {
        _uiState.update { it.copy(permissionExplanation = null) }
    }

    /**
     * Explicitly starts monitoring even when non-critical or acknowledged permissions are missing,
     * maintaining honest representation of capabilities without false claims.
     */
    fun startMonitoringWithLimitations(context: Context? = null) {
        _uiState.update { it.copy(permissionExplanation = null) }
        toggleMonitoring(context)
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure pipeline stops if ViewModel is destroyed, unless background service is actively running
        if (!com.manish.vahakvigil.service.SafetyMonitoringService.isServiceRunning.value) {
            pipeline.stop()
        }
    }

    companion object {
        fun provideFactory(
            repository: SensorRepository,
            medicalProfileRepository: MedicalProfileRepository? = null,
            contactRepository: ContactRepository? = null,
            locationProvider: LocationProvider? = null,
            smsSender: EmergencySmsSender? = null,
            timezoneManager: com.manish.vahakvigil.data.preferences.TimezoneManager? = null,
            pipeline: CrashDetectionPipeline? = null,
            ridePlanningCoordinator: RidePlanningCoordinator? = null,
            rideSessionRepository: RideSessionRepository? = null,
            emergencyIncidentRepository: EmergencyIncidentRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val resolvedPipeline = pipeline ?: run {
                    val compiler = if (medicalProfileRepository != null && contactRepository != null) {
                        EmergencyDispatchDataCompiler(medicalProfileRepository, contactRepository, locationProvider)
                    } else {
                        null
                    }
                    val orchestrator = if (smsSender != null) {
                        EmergencyActionOrchestrator(SmsEmergencyActionExecutor(smsSender))
                    } else {
                        EmergencyActionOrchestrator()
                    }
                    CrashDetectionPipeline(
                        sensorRepository = repository,
                        dispatchDataCompiler = compiler,
                        locationProvider = locationProvider,
                        actionOrchestrator = orchestrator
                    )
                }
                return HomeViewModel(
                    sensorRepository = repository,
                    pipeline = resolvedPipeline,
                    locationProvider = locationProvider,
                    timezoneManager = timezoneManager,
                    ridePlanningCoordinator = ridePlanningCoordinator ?: RidePlanningCoordinator(
                        EmptyDestinationSearchRepository(),
                        UnconfiguredRideRoutingRepository()
                    ),
                    rideSessionRepository = rideSessionRepository,
                    emergencyIncidentRepository = emergencyIncidentRepository
                ) as T
            }
        }
    }
}
