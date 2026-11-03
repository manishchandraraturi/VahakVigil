package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.RideLocationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Pure Kotlin abstraction for retrieving a location snapshot for emergency dispatch
 * and observing continuous location updates for live ride monitoring.
 *
 * Independent of Android framework classes and third-party location libraries.
 */
interface LocationProvider {

    /**
     * Retrieves the current location snapshot.
     *
     * Implementations must return [EmergencyLocation.Unavailable] if permissions are missing,
     * hardware is disabled, location cannot be retrieved, or coordinates are invalid.
     *
     * @return An [EmergencyLocation] snapshot (either [EmergencyLocation.Available] or [EmergencyLocation.Unavailable]).
     */
    suspend fun getCurrentLocation(): EmergencyLocation

    /**
     * Provides a cold stream of live location updates for an active ride.
     *
     * When collected, registers for location updates; when collection ends or is cancelled,
     * immediately releases and unregisters location listeners.
     *
     * @return A [Flow] of [RideLocationState].
     */
    fun getLocationUpdates(): Flow<RideLocationState> = emptyFlow()
}
