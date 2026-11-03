package com.manish.vahakvigil.domain.model

/**
 * Pure Kotlin representation of geographic coordinates for emergency dispatch.
 * Decoupled from Android Location APIs.
 */
sealed interface EmergencyLocation {
    /**
     * Location coordinates are unavailable (e.g. GPS not enabled, hardware missing,
     * or milestone placeholder prior to location integration).
     */
    data object Unavailable : EmergencyLocation

    /**
     * Valid geographical location coordinates for future GPS integration.
     *
     * @param latitude Geographical latitude in degrees.
     * @param longitude Geographical longitude in degrees.
     * @param accuracyMeters Optional estimated horizontal accuracy radius in meters.
     * @param timestampMs Optional timestamp when location fix was captured.
     */
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float? = null,
        val timestampMs: Long? = null
    ) : EmergencyLocation {
        val mapsUrl: String
            get() = "https://maps.google.com/?q=$latitude,$longitude"
    }

    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0

        /**
         * Validates whether the given coordinates fall within standard geographic bounds.
         * Latitude must be in [-90.0, 90.0] and Longitude must be in [-180.0, 180.0].
         * NaN and infinite values are rejected.
         */
        fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
            return !latitude.isNaN() && !latitude.isInfinite() &&
                    !longitude.isNaN() && !longitude.isInfinite() &&
                    latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE &&
                    longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE
        }
    }
}
