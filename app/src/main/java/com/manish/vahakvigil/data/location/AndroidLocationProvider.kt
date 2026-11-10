package com.manish.vahakvigil.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.location.LiveLocationAvailabilityGate
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyLocationSource
import com.manish.vahakvigil.domain.model.RideLocationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android platform implementation of [LocationProvider] using Google Play Services
 * [FusedLocationProviderClient].
 *
 * Responsibilities & Safety guarantees:
 * - Checks [Manifest.permission.ACCESS_FINE_LOCATION] and [Manifest.permission.ACCESS_COARSE_LOCATION].
 *   If neither is granted, safely returns [EmergencyLocation.Unavailable] without crashing.
 * - Concurrently initiates fresh high-accuracy GPS fix and cached last-known location queries.
 * - Prioritizes fresh location if returned within [FRESH_LOCATION_TIMEOUT_MS] (4.5 seconds).
 * - Falls back to last known location if fresh GPS fails, is null, or times out.
 * - Enforces an absolute 5-second timeout on overall location retrieval via [LOCATION_TIMEOUT_MS].
 * - Validates latitude in [-90.0, 90.0] and longitude in [-180.0, 180.0] via [EmergencyLocation.isValidCoordinate].
 * - Extracts accuracy and timestamp if available without fabricating synthetic values.
 * - Catches any [SecurityException] or unexpected exception and returns [EmergencyLocation.Unavailable].
 * - Strictly respects privacy: NEVER logs coordinates, accuracy, or personal data.
 */
class AndroidLocationProvider(
    private val context: Context? = null,
    private val fusedLocationClient: FusedLocationProviderClient? = null
) : LocationProvider {

    private val resolvedClient: FusedLocationProviderClient? by lazy {
        fusedLocationClient ?: context?.let { LocationServices.getFusedLocationProviderClient(it) }
    }

    internal var permissionChecker: (Context?, String) -> Boolean = { ctx, perm ->
        if (ctx == null) true
        else ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }
    internal var locationEnabledChecker: (Context?) -> Boolean = { ctx ->
        val manager = ctx?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        manager == null || LocationManagerCompat.isLocationEnabled(manager)
    }

    internal var freshLocationFetcher: (suspend () -> EmergencyLocation?)? = null
    internal var lastLocationFetcher: (suspend () -> EmergencyLocation?)? = null
    internal var wallClockMillisProvider: () -> Long = System::currentTimeMillis

    internal var freshLocationTimeoutMs: Long = FRESH_LOCATION_TIMEOUT_MS
    internal var totalLocationTimeoutMs: Long = LOCATION_TIMEOUT_MS

    override suspend fun getCurrentLocation(): EmergencyLocation {
        val hasFine = permissionChecker(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = permissionChecker(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasFine && !hasCoarse) {
            return EmergencyLocation.Unavailable
        }

        return try {
            withTimeoutOrNull(totalLocationTimeoutMs) { fetchLocation() } ?: EmergencyLocation.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            EmergencyLocation.Unavailable
        } catch (e: Exception) {
            EmergencyLocation.Unavailable
        }
    }

    private suspend fun fetchLocation(): EmergencyLocation = coroutineScope {
        // 1. Kick off cached last-known location query immediately (typically completes in <50ms)
        val lastLocationDeferred = async {
            lastLocationFetcher?.invoke() ?: queryLastLocation()
        }

        // 2. Concurrently attempt fresh high-accuracy GPS acquisition
        val freshLocationDeferred = async {
            freshLocationFetcher?.invoke() ?: queryFreshLocation()
        }

        // Wait for fresh GPS acquisition up to freshLocationTimeoutMs (defaults to 4.5s within 5.0s budget)
        val freshLocation = withTimeoutOrNull(freshLocationTimeoutMs) {
            try {
                freshLocationDeferred.await()
            } catch (_: CancellationException) {
                null
            } catch (_: Exception) {
                null
            }
        }

        if (freshLocation != null && freshLocation is EmergencyLocation.Available) {
            lastLocationDeferred.cancel()
            freshLocation
        } else {
            // Fresh GPS was null, unavailable, failed, or timed out — cancel fresh request and fall back to last known location
            freshLocationDeferred.cancel()
            val fallback = try {
                lastLocationDeferred.await()
            } catch (_: Exception) {
                null
            }
            if (fallback is EmergencyLocation.Available && isAcceptableLastKnown(fallback)) {
                fallback.copy(source = EmergencyLocationSource.LAST_KNOWN)
            } else {
                EmergencyLocation.Unavailable
            }
        }
    }

    private fun isAcceptableLastKnown(location: EmergencyLocation.Available): Boolean {
        val timestamp = location.timestampMs ?: return false
        val age = wallClockMillisProvider() - timestamp
        return age in 0L..MAX_LAST_KNOWN_AGE_MS
    }

    private suspend fun queryFreshLocation(): EmergencyLocation? {
        val client = resolvedClient ?: return null
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cts.cancel() }
            try {
                client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).addOnSuccessListener { location: Location? ->
                    continuation.resume(location.toEmergencyLocation(EmergencyLocationSource.CURRENT_FIX))
                }.addOnFailureListener {
                    continuation.resume(null)
                }.addOnCanceledListener {
                    continuation.resume(null)
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    private suspend fun queryLastLocation(): EmergencyLocation? {
        val client = resolvedClient ?: return null
        return suspendCancellableCoroutine { continuation ->
            try {
                client.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        continuation.resume(location.toEmergencyLocation(EmergencyLocationSource.LAST_KNOWN))
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        continuation.resume(null)
                    }
            } catch (e: SecurityException) {
                continuation.resume(null)
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    internal fun Location?.toEmergencyLocation(source: EmergencyLocationSource = EmergencyLocationSource.CURRENT_FIX): EmergencyLocation {
        if (this == null) return EmergencyLocation.Unavailable

        val lat = latitude
        val lon = longitude

        if (!EmergencyLocation.isValidCoordinate(lat, lon)) {
            return EmergencyLocation.Unavailable
        }

        val accuracy = if (hasAccuracy() && !accuracy.isNaN() && accuracy >= 0f) {
            accuracy
        } else {
            null
        }

        val timestamp = if (time > 0L) time else null

        return EmergencyLocation.Available(
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            timestampMs = timestamp,
            source = source
        )
    }

    internal var liveLocationUpdatesFlow: Flow<RideLocationState>? = null

    override fun getLocationUpdates(): Flow<RideLocationState> {
        liveLocationUpdatesFlow?.let { return it }

        val hasFine = permissionChecker(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = permissionChecker(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasFine && !hasCoarse) {
            return flowOf(RideLocationState.PermissionDenied)
        }

        if (!locationEnabledChecker(context)) {
            return flowOf(RideLocationState.GpsDisabled)
        }

        val client = resolvedClient ?: return flowOf(RideLocationState.LocationUnavailable)

        return callbackFlow {
            // Initially emit WaitingForLocation so UI knows we're actively acquiring a fix
            trySend(RideLocationState.WaitingForLocation)
            val availabilityGate = LiveLocationAvailabilityGate()
            val requestStartedAt = SystemClock.elapsedRealtime()
            val lastWatchdogState = java.util.concurrent.atomic.AtomicReference<RideLocationState?>(null)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LIVE_LOCATION_INTERVAL_MS
            ).apply {
                setMinUpdateIntervalMillis(LIVE_LOCATION_MIN_INTERVAL_MS)
                setMinUpdateDistanceMeters(LIVE_LOCATION_MIN_DISPLACEMENT_METERS)
                setWaitForAccurateLocation(false)
            }.build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation
                    if (location != null && EmergencyLocation.isValidCoordinate(location.latitude, location.longitude)) {
                        availabilityGate.onValidFix(SystemClock.elapsedRealtime())
                        lastWatchdogState.set(null)
                        val accuracy = if (location.hasAccuracy() && !location.accuracy.isNaN() && location.accuracy >= 0f) {
                            location.accuracy
                        } else null

                        val timestamp = if (location.time > 0L) location.time else System.currentTimeMillis()
                        val speed = if (location.hasSpeed() && !location.speed.isNaN() && location.speed >= 0f) location.speed else null
                        val bearing = if (location.hasBearing() && !location.bearing.isNaN()) location.bearing else null

                        trySend(
                            RideLocationState.Available(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracyMeters = accuracy,
                                timestampMs = timestamp,
                                speedMps = speed,
                                bearingDegrees = bearing
                            )
                        )
                    }
                }

                override fun onLocationAvailability(availability: com.google.android.gms.location.LocationAvailability) {
                    // A false availability report during startup is not signal loss: no live fix
                    // has existed yet. Keep the UI in acquisition state until the first fix arrives.
                    availabilityGate.onAvailabilityChanged(availability.isLocationAvailable)?.let { state ->
                        lastWatchdogState.set(state)
                        trySend(state)
                    }
                }
            }

            launch {
                while (isActive) {
                    delay(LOCATION_WATCHDOG_INTERVAL_MS)
                    val timeoutState = availabilityGate.onWatchdog(
                        nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        requestStartedElapsedRealtimeMs = requestStartedAt,
                        initialFixTimeoutMs = LIVE_INITIAL_FIX_TIMEOUT_MS,
                        staleFixTimeoutMs = LIVE_STALE_FIX_TIMEOUT_MS
                    )
                    if (timeoutState != null && lastWatchdogState.getAndSet(timeoutState) != timeoutState) {
                        trySend(timeoutState)
                    }
                }
            }

            try {
                client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                    .addOnFailureListener {
                        // The request may fail asynchronously (for example when Play
                        // Services cannot satisfy the request). Surface that failure
                        // instead of leaving the map in acquisition forever.
                        trySend(RideLocationState.LocationUnavailable)
                        close()
                    }
                    .addOnCanceledListener {
                        trySend(RideLocationState.LocationUnavailable)
                        close()
                    }
            } catch (e: SecurityException) {
                trySend(RideLocationState.PermissionDenied)
                close(e)
            } catch (e: Exception) {
                trySend(RideLocationState.LocationUnavailable)
                close(e)
            }

            awaitClose {
                try {
                    client.removeLocationUpdates(callback)
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        const val LOCATION_TIMEOUT_MS = 5_000L
        const val MAX_LAST_KNOWN_AGE_MS = 15 * 60_000L
        const val FRESH_LOCATION_TIMEOUT_MS = 4_500L
        const val LIVE_LOCATION_INTERVAL_MS = 5_000L
        const val LIVE_LOCATION_MIN_INTERVAL_MS = 3_000L
        const val LIVE_LOCATION_MIN_DISPLACEMENT_METERS = 5.0f
        const val LIVE_INITIAL_FIX_TIMEOUT_MS = 30_000L
        const val LIVE_STALE_FIX_TIMEOUT_MS = 20_000L
        const val LOCATION_WATCHDOG_INTERVAL_MS = 5_000L
    }
}
