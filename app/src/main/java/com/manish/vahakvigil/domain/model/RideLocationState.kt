package com.manish.vahakvigil.domain.model

/**
 * Represents the live location state of an active motorcycle ride.
 *
 * Exposes the minimum state needed by the UI and path tracking:
 * - current latitude and longitude
 * - accuracy radius in meters
 * - timestamp
 * - optional speed (m/s) and bearing (degrees)
 * - availability and failure states
 */
sealed interface RideLocationState {

    /**
     * Ride monitoring is inactive; no location listeners are registered.
     */
    data object Inactive : RideLocationState

    /**
     * Ride monitoring is active, but waiting for the initial GPS/fused location fix.
     */
    data object WaitingForLocation : RideLocationState

    /**
     * Valid live location fix received for the active ride.
     *
     * @param latitude Geographical latitude in degrees [-90.0, 90.0].
     * @param longitude Geographical longitude in degrees [-180.0, 180.0].
     * @param accuracyMeters Estimated horizontal accuracy radius in meters.
     * @param timestampMs Epoch millisecond timestamp of the fix.
     * @param speedMps Speed in meters per second, if available from GPS.
     * @param bearingDegrees Heading in degrees [0, 360), if available from GPS.
     */
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float? = null,
        val timestampMs: Long = System.currentTimeMillis(),
        val speedMps: Float? = null,
        val bearingDegrees: Float? = null
    ) : RideLocationState {
        val mapsUrl: String
            get() = "https://maps.google.com/?q=$latitude,$longitude"
    }

    /**
     * Location permissions (FINE or COARSE) are denied by the user.
     */
    data object PermissionDenied : RideLocationState

    /** System-wide device location setting is disabled. */
    data object GpsDisabled : RideLocationState

    /**
     * Location hardware (GPS) or Google Play Services is disabled or unavailable.
     */
    data object LocationUnavailable : RideLocationState

    /**
     * Location fix was previously available but is temporarily lost (e.g. tunnel, dense canyon).
     */
    data object LocationTemporarilyUnavailable : RideLocationState
}

/**
 * Immutable representation of a single coordinate point in a recorded ride path.
 */
data class RideLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
