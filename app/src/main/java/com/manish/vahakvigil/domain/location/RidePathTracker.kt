package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.RideLocationPoint
import com.manish.vahakvigil.domain.model.RideLocationState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages the in-memory movement polyline for an active motorcycle ride.
 *
 * Guarantees:
 * - Thread-safe in-memory point storage.
 * - Rejects impossible/invalid coordinates using [EmergencyLocation.isValidCoordinate].
 * - Filters out inaccurate readings ([MAX_ACCEPTABLE_ACCURACY_METERS] = 30m).
 * - Filters out excessively stale points ([MAX_STALENESS_MILLIS] = 60s).
 * - Ignores stationary jitter using minimum displacement plus the reported accuracy radii.
 * - Enforces bounded memory ([MAX_POINTS] = 2000) by trimming oldest points during ultra-long rides.
 * - Zero network calls, zero database writes; strictly local in-memory visualization buffer.
 */
class RidePathTracker(
    private val maxPoints: Int = MAX_POINTS,
    private val maxAcceptableAccuracyMeters: Float = MAX_ACCEPTABLE_ACCURACY_METERS,
    private val minDisplacementMeters: Double = MIN_DISPLACEMENT_METERS,
    private val maxStalenessMillis: Long = MAX_STALENESS_MILLIS,
    private val maxPlausibleSpeedMetersPerSecond: Double = MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND
) {

    private val points = mutableListOf<RideLocationPoint>()

    /**
     * Evaluates and conditionally appends a new location point to the active ride path.
     *
     * @param point The incoming location candidate.
     * @param currentTimeMs Optional system time reference for deterministic unit testing.
     * @return `true` if the point was accepted and added; `false` if rejected or ignored.
     */
    @Synchronized
    fun addPoint(
        point: RideLocationPoint,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. Coordinate validation
        if (!EmergencyLocation.isValidCoordinate(point.latitude, point.longitude)) {
            return false
        }

        // 2. Accuracy validation (reject noisy cellular/multipath readings)
        val accuracy = point.accuracyMeters
        if (accuracy != null && (accuracy < 0f || accuracy > maxAcceptableAccuracyMeters)) {
            return false
        }

        // 3. Staleness validation (reject timestamps older than maxStalenessMillis)
        if (currentTimeMs - point.timestampMs > maxStalenessMillis) {
            return false
        }
        if (point.timestampMs - currentTimeMs > MAX_FUTURE_TIMESTAMP_MILLIS) {
            return false
        }

        // 4. Duplicate / near-duplicate stationary filter
        if (points.isNotEmpty()) {
            val last = points.last()
            // Timestamp monotonicity check (reject retro-temporal points)
            if (point.timestampMs <= last.timestampMs) {
                return false
            }

            val distanceMeters = calculateDistanceMeters(
                lat1 = last.latitude,
                lon1 = last.longitude,
                lat2 = point.latitude,
                lon2 = point.longitude
            )
            val uncertaintyThreshold = if (accuracy != null && last.accuracyMeters != null) {
                accuracy.toDouble() + last.accuracyMeters.toDouble()
            } else {
                minDisplacementMeters
            }
            if (distanceMeters < maxOf(minDisplacementMeters, uncertaintyThreshold)) {
                return false // Stationary jitter; do not add redundant point
            }
            val elapsedSeconds = (point.timestampMs - last.timestampMs) / 1000.0
            if (distanceMeters / elapsedSeconds > maxPlausibleSpeedMetersPerSecond) {
                return false // Ignore jumps that cannot represent a motorcycle GPS fix.
            }
        }

        // 5. Bounded memory: keep size within maxPoints
        if (points.size >= maxPoints) {
            points.removeAt(0)
        }

        points.add(point)
        return true
    }

    /**
     * Convenience method to add directly from a [RideLocationState.Available].
     */
    @Synchronized
    fun addFromState(
        state: RideLocationState.Available,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        return addPoint(
            point = RideLocationPoint(
                latitude = state.latitude,
                longitude = state.longitude,
                accuracyMeters = state.accuracyMeters,
                timestampMs = state.timestampMs
            ),
            currentTimeMs = currentTimeMs
        )
    }

    /**
     * Returns an immutable snapshot copy of the current active ride path.
     */
    @Synchronized
    fun getCurrentPath(): List<RideLocationPoint> {
        return points.toList()
    }

    /**
     * Clears and resets the path when starting a new ride.
     */
    @Synchronized
    fun reset() {
        points.clear()
    }

    /**
     * Clears all recorded points upon stopping the ride.
     */
    @Synchronized
    fun clear() {
        points.clear()
    }

    @Synchronized
    fun removeLast(point: RideLocationPoint): Boolean {
        if (points.lastOrNull() != point) return false
        points.removeAt(points.lastIndex)
        return true
    }

    /**
     * Returns the current number of points in the path.
     */
    @Synchronized
    fun size(): Int = points.size

    companion object {
        const val MAX_POINTS = 2000
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 30.0f
        const val MIN_DISPLACEMENT_METERS = 3.0
        const val MAX_STALENESS_MILLIS = 60_000L
        // Keep high motorcycle speeds (270 km/h) while filtering impossible fixes/spikes.
        const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 75.0
        const val MAX_FUTURE_TIMESTAMP_MILLIS = 5_000L

        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /**
         * Computes the great-circle distance between two coordinates in meters using the Haversine formula.
         */
        fun calculateDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}
