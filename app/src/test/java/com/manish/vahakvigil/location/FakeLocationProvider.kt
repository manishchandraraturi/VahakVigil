package com.manish.vahakvigil.location

import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.model.EmergencyLocation
import kotlinx.coroutines.delay

/**
 * Deterministic in-memory fake implementation of [LocationProvider] for unit testing.
 *
 * Supports:
 * - Simulating available location fixes
 * - Simulating unavailable location
 * - Simulating permission failure
 * - Simulating provider failure
 * - Simulating delays and timeouts
 * - Simulating unexpected exceptions
 * - Validating coordinates via [EmergencyLocation.isValidCoordinate]
 */
class FakeLocationProvider(
    var simulatedLocation: EmergencyLocation = EmergencyLocation.Unavailable,
    var shouldSimulatePermissionFailure: Boolean = false,
    var shouldSimulateProviderFailure: Boolean = false,
    var delayMs: Long = 0L,
    var shouldThrowException: Boolean = false
) : LocationProvider {

    var callCount: Int = 0
        private set

    override suspend fun getCurrentLocation(): EmergencyLocation {
        callCount++
        if (delayMs > 0L) {
            delay(delayMs)
        }
        if (shouldThrowException) {
            throw RuntimeException("Simulated GPS failure")
        }
        if (shouldSimulatePermissionFailure) {
            return EmergencyLocation.Unavailable
        }
        if (shouldSimulateProviderFailure) {
            return EmergencyLocation.Unavailable
        }
        return when (val loc = simulatedLocation) {
            is EmergencyLocation.Available -> {
                if (EmergencyLocation.isValidCoordinate(loc.latitude, loc.longitude)) {
                    loc
                } else {
                    EmergencyLocation.Unavailable
                }
            }
            is EmergencyLocation.Unavailable -> EmergencyLocation.Unavailable
        }
    }

    var liveLocationFlow: kotlinx.coroutines.flow.Flow<com.manish.vahakvigil.domain.model.RideLocationState>? = null
    var locationUpdatesCallCount: Int = 0
        private set

    override fun getLocationUpdates(): kotlinx.coroutines.flow.Flow<com.manish.vahakvigil.domain.model.RideLocationState> {
        locationUpdatesCallCount++
        return liveLocationFlow ?: kotlinx.coroutines.flow.emptyFlow()
    }

    fun reset() {
        callCount = 0
        locationUpdatesCallCount = 0
        simulatedLocation = EmergencyLocation.Unavailable
        shouldSimulatePermissionFailure = false
        shouldSimulateProviderFailure = false
        delayMs = 0L
        shouldThrowException = false
        liveLocationFlow = null
    }
}
