package com.manish.vahakvigil.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.manish.vahakvigil.MainActivity
import com.manish.vahakvigil.R
import com.manish.vahakvigil.VahakVigilApp
import com.manish.vahakvigil.domain.repository.SensorRepository
import android.os.PowerManager
import com.manish.vahakvigil.domain.feedback.EmergencyFeedbackController
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.location.RideSessionRecorder
import com.manish.vahakvigil.data.sensor.SensorSessionRecorder
import com.manish.vahakvigil.domain.model.CrashDetectionState
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.RideLocationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground Service responsible for persistent background safety monitoring in VahakVigil.
 *
 * Phase 6, Step 2: Connect Service to existing SensorRepository.
 * - Manages the Android Foreground Service lifecycle (start, stop, recreate).
 * - Creates and registers the required notification channel on Android 8.0+ (API 26+).
 * - Displays a persistent ongoing notification with clear VahakVigil safety branding.
 * - Strictly complies with Android 14+ (API 34) foreground service type declarations.
 * - Connects to the existing singleton [SensorRepository], starting/stopping monitoring
 *   in sync with the service lifecycle without duplicating sensor infrastructure or detection logic.
 * - Acquires a non-reference-counted PARTIAL_WAKE_LOCK to maintain CPU execution when screen is off (CB-02).
 * - Drives background emergency siren/vibration feedback and notification updates (CB-01).
 */
class SafetyMonitoringService : Service() {

    companion object {
        const val CHANNEL_ID = "vahak_vigil_safety_channel"
        const val CHANNEL_NAME = "Ride Safety Monitoring"
        const val CHANNEL_DESCRIPTION = "Persistent background monitoring for ride safety and crash detection"
        const val EMERGENCY_CHANNEL_ID = "vahak_vigil_emergency_channel"
        const val EMERGENCY_CHANNEL_NAME = "Emergency Crash Alerts"
        const val EMERGENCY_CHANNEL_DESCRIPTION = "High-priority crash detection alerts and emergency countdown triggers"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.manish.vahakvigil.action.START_SAFETY_MONITORING"
        const val ACTION_STOP = "com.manish.vahakvigil.action.STOP_SAFETY_MONITORING"

        const val NOTIFICATION_TITLE = "VahakVigil Active"
        const val NOTIFICATION_TEXT = "Ride safety monitoring active in background"
        const val NOTIFICATION_SUBTEXT = "Safety System"
        const val WAKE_LOCK_TAG = "VahakVigil:SafetyMonitoring"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _currentRideLocation = MutableStateFlow<RideLocationState>(RideLocationState.Inactive)
        val currentRideLocation: StateFlow<RideLocationState> = _currentRideLocation.asStateFlow()

        @androidx.annotation.VisibleForTesting
        internal var testSensorRepository: SensorRepository? = null

        @androidx.annotation.VisibleForTesting
        internal var testLocationProvider: LocationProvider? = null

        @androidx.annotation.VisibleForTesting
        internal var testCrashDetectionPipeline: com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline? = null

        @androidx.annotation.VisibleForTesting
        internal var testFeedbackController: EmergencyFeedbackController? = null

        @androidx.annotation.VisibleForTesting
        internal var testWakeLockHandle: WakeLockHandle? = null

        @androidx.annotation.VisibleForTesting
        internal var testServiceDispatcher: kotlinx.coroutines.CoroutineDispatcher? = null

        @androidx.annotation.VisibleForTesting
        internal var testRideSessionRecorder: RideSessionRecorder? = null

        @androidx.annotation.VisibleForTesting
        internal var testSensorSessionRecorder: SensorSessionRecorder? = null

        /**
         * Helper to request starting the foreground safety monitoring service.
         */
        fun startService(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Helper to request stopping the foreground safety monitoring service.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        (testServiceDispatcher ?: kotlinx.coroutines.Dispatchers.Default) + serviceJob
    )
    private val sessionPersistenceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + (testServiceDispatcher ?: kotlinx.coroutines.Dispatchers.IO)
    )
    private var sessionFinishJob: Job? = null
    private var sessionStartJob: Job? = null
    private var serviceStopCleanupJob: Job? = null
    private val rideSessionRecorder: RideSessionRecorder? by lazy {
        // Service unit tests inject the monitoring dependencies without a real Android
        // Application. Session persistence is covered through RideSessionRecorder tests.
        testRideSessionRecorder
            ?: if (testSensorRepository != null) null
        else (application as? VahakVigilApp)?.rideSessionRepository?.let(::RideSessionRecorder)
    }
    private val sensorSessionRecorder: SensorSessionRecorder? by lazy {
        testSensorSessionRecorder
            ?: if (testSensorRepository != null) null
            else (application as? VahakVigilApp)?.sensorSessionRecorder
    }

    internal val sensorRepository: SensorRepository
        get() = testSensorRepository
            ?: (application as? VahakVigilApp)?.sensorRepository
            ?: (applicationContext as? VahakVigilApp)?.sensorRepository
            ?: throw IllegalStateException("SensorRepository is unavailable from application context.")

    internal val pipeline: com.manish.vahakvigil.domain.pipeline.CrashDetectionPipeline
        get() = testCrashDetectionPipeline
            ?: (application as? VahakVigilApp)?.crashDetectionPipeline
            ?: (applicationContext as? VahakVigilApp)?.crashDetectionPipeline
            ?: throw IllegalStateException("CrashDetectionPipeline is unavailable from application context.")

    internal val feedbackController: EmergencyFeedbackController
        get() = testFeedbackController
            ?: (application as? VahakVigilApp)?.emergencyFeedbackController
            ?: (applicationContext as? VahakVigilApp)?.emergencyFeedbackController
            ?: com.manish.vahakvigil.data.feedback.AndroidEmergencyFeedbackController(applicationContext)

    internal val locationProvider: LocationProvider
        get() = testLocationProvider
            ?: (application as? VahakVigilApp)?.locationProvider
            ?: (applicationContext as? VahakVigilApp)?.locationProvider
            ?: com.manish.vahakvigil.data.location.AndroidLocationProvider(applicationContext)

    private var wakeLock: WakeLockHandle? = null
    private var stateObservationJob: Job? = null
    private var locationUpdatesJob: Job? = null
    @Volatile private var isEmergencyFeedbackActive = false

    @Synchronized
    internal fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = testWakeLockHandle ?: try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                    setReferenceCounted(false)
                }?.let { AndroidWakeLockHandle(it) }
            } catch (_: Exception) {
                null
            }
        }
        wakeLock?.let { lock ->
            if (!lock.isHeld) {
                try {
                    lock.acquire()
                } catch (_: Exception) {}
            }
        }
    }

    @Synchronized
    internal fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                try {
                    lock.release()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    internal var isExplicitlyStopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                isExplicitlyStopped = true
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                // If action is null, service was recreated by system after being killed (START_STICKY)
                if (intent == null && isExplicitlyStopped) {
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } catch (_: Exception) {}
                    return START_NOT_STICKY
                }
                isExplicitlyStopped = false
                startForegroundWithNotification()
            }
            else -> {
                // Any other action defaults to maintaining foreground state
                startForegroundWithNotification()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        handleTaskRemoved(rootIntent)
    }

    internal fun handleTaskRemoved(rootIntent: Intent?) {
        if (!isExplicitlyStopped && _isServiceRunning.value) {
            // User swiped the app away from Android Recents while monitoring is active.
            // On Samsung One UI and modern Android:
            // Maintain foreground notification and do NOT stop the service or pipeline.
            // Ensure wake lock and monitoring continue uninterrupted.
            // START_STICKY will ensure restart if the process is terminated.
            try {
                if (pipeline.state.value.isEmergencyModeActive) {
                    val remaining = (pipeline.state.value.emergencyCountdownRemainingMillis / 1000).coerceAtLeast(0L).toInt()
                    updateEmergencyNotification(isEmergency = true, remainingSeconds = remaining)
                } else {
                    updateEmergencyNotification(isEmergency = false)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            super.onDestroy()
        } catch (_: Exception) {
            // Handled when executing in unit tests without android.app.Service mocking
        }
        onServiceDestroyed()
    }

    internal fun onServiceDestroyed() {
        stopLocationUpdates()
        stateObservationJob?.cancel()
        stateObservationJob = null
        if (isEmergencyFeedbackActive) {
            isEmergencyFeedbackActive = false
            try {
                feedbackController.stopAlert()
            } catch (_: Exception) {}
        }
        releaseWakeLock()

        if (isExplicitlyStopped) {
            onServiceStopped()
        } else {
            // Unexpected destruction (e.g. system low memory kill or task removal).
            // Preserve pipeline monitoring and emergency state for START_STICKY restart.
            _isServiceRunning.value = false
        }
    }

    internal fun onServiceStarted() {
        acquireWakeLock()
        val alreadyRunning = _isServiceRunning.value && pipeline.isRunning
        _isServiceRunning.value = true
        if (!alreadyRunning) {
            try {
                pipeline.start(serviceScope)
            } catch (_: Exception) {
            }
        }
        startStateObservation()
        val rideRecorder = rideSessionRecorder
        val sensorRecorder = sensorSessionRecorder
        if (rideRecorder != null && sensorRecorder != null && sessionStartJob?.isActive != true) {
            sessionStartJob = serviceScope.launch {
                sessionFinishJob?.join()
                try {
                    val sessionId = rideRecorder.startOrResume()
                    if (sessionId == null) {
                        sensorRecorder.reportUnavailable("Ride session could not be initialized")
                    } else if (!sensorRecorder.start(sessionId)) {
                        // Recording failure is diagnostic only; crash monitoring continues.
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    sensorRecorder.reportUnavailable(
                        "Sensor recording could not start: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
        startLocationUpdates()
    }

    internal fun startLocationUpdates() {
        locationUpdatesJob?.cancel()
        _currentRideLocation.value = RideLocationState.WaitingForLocation
        locationUpdatesJob = serviceScope.launch {
            try {
                locationProvider.getLocationUpdates().collect { locationState ->
                    _currentRideLocation.value = locationState
                    if (locationState is RideLocationState.Available) {
                        rideSessionRecorder?.record(locationState)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _currentRideLocation.value = RideLocationState.LocationUnavailable
            }
        }
    }

    internal fun stopLocationUpdates() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = null
        _currentRideLocation.value = RideLocationState.Inactive
    }

    internal fun startStateObservation() {
        stateObservationJob?.cancel()
        stateObservationJob = serviceScope.launch {
            try {
                pipeline.state.collect { state ->
                    handlePipelineEmergencyState(state)
                }
            } catch (_: Exception) {}
        }
    }

    internal fun handlePipelineEmergencyState(state: CrashDetectionState) {
        when (state.emergencyCountdownState) {
            EmergencyCountdownState.COUNTING_DOWN -> {
                if (!isEmergencyFeedbackActive) {
                    isEmergencyFeedbackActive = true
                    try {
                        feedbackController.startAlert()
                    } catch (_: Exception) {}
                }
                val remainingSeconds = (state.emergencyCountdownRemainingMillis / 1000).coerceAtLeast(0L).toInt()
                updateEmergencyNotification(isEmergency = true, remainingSeconds = remainingSeconds)
            }
            EmergencyCountdownState.EXPIRED,
            EmergencyCountdownState.CANCELLED,
            EmergencyCountdownState.IDLE -> {
                if (isEmergencyFeedbackActive) {
                    isEmergencyFeedbackActive = false
                    try {
                        feedbackController.stopAlert()
                    } catch (_: Exception) {}
                }
                updateEmergencyNotification(isEmergency = false)
            }
        }
    }

    internal fun onServiceStopped() {
        _isServiceRunning.value = false
        stopLocationUpdates()
        stateObservationJob?.cancel()
        stateObservationJob = null
        if (isEmergencyFeedbackActive) {
            isEmergencyFeedbackActive = false
            try {
                feedbackController.stopAlert()
            } catch (_: Exception) {}
        }
        releaseWakeLock()
        try {
            pipeline.stop()
        } catch (_: Exception) {
        }
        val rideRecorder = rideSessionRecorder
        val sensorRecorder = sensorSessionRecorder
        if ((rideRecorder != null || sensorRecorder != null) && sessionFinishJob?.isActive != true) {
            val pendingStart = sessionStartJob
            sessionFinishJob = sessionPersistenceScope.launch {
                pendingStart?.join()
                try {
                    sensorRecorder?.stopAndFlush()
                } catch (_: CancellationException) {
                    throw CancellationException("Sensor recording flush was cancelled")
                } catch (_: Exception) {
                    // Recording failure must not prevent ride finalization or safety cleanup.
                }
                try {
                    rideRecorder?.finish()
                } catch (_: Exception) {
                    // RideSessionRecorder already contains repository failures; retain service safety.
                }
            }
        }
        serviceJob.cancelChildren()
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
            val hasFine = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (hasFine || hasCoarse) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10 (API 29)
            val hasFine = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        onServiceStarted()
    }

    private fun stopForegroundService() {
        onServiceStopped()
        val finishJob = sessionFinishJob
        if (finishJob?.isActive != true) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if (serviceStopCleanupJob?.isActive != true) {
            serviceStopCleanupJob = sessionPersistenceScope.launch {
                finishJob.join()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val normalChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                EMERGENCY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = EMERGENCY_CHANNEL_DESCRIPTION
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(normalChannel)
            notificationManager?.createNotificationChannel(emergencyChannel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSubText(NOTIFICATION_SUBTEXT)
            .setSmallIcon(R.drawable.ic_safety_service)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    internal fun updateEmergencyNotification(isEmergency: Boolean, remainingSeconds: Int = 0) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return
            val notification = if (isEmergency) {
                buildEmergencyNotification(remainingSeconds)
            } else {
                buildNotification()
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Handled when executing in unit tests without android.app.NotificationManager mocking
        }
    }

    private fun buildEmergencyNotification(remainingSeconds: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
            .setContentTitle("CRASH DETECTED — SOS in ${remainingSeconds}s")
            .setContentText("Emergency countdown active. Tap to view alert or cancel.")
            .setSubText("SOS Alert")
            .setSmallIcon(R.drawable.ic_safety_service)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }
}

/**
 * Abstraction wrapping Android [PowerManager.WakeLock] to facilitate pure JVM unit testing.
 */
interface WakeLockHandle {
    val isHeld: Boolean
    fun acquire()
    fun release()
}

internal class AndroidWakeLockHandle(private val wakeLock: PowerManager.WakeLock) : WakeLockHandle {
    override val isHeld: Boolean get() = wakeLock.isHeld
    override fun acquire() = wakeLock.acquire()
    override fun release() = wakeLock.release()
}
