package com.manish.vahakvigil.domain.model

/**
 * Verification status of an emergency hospital's trauma/emergency capability.
 *
 * Capabilities must NEVER be invented. If the data source does not explicitly
 * confirm trauma capability, it must be marked [UNVERIFIED] or [UNKNOWN].
 */
enum class EmergencyCapabilityStatus {
    /** Hospital has verified 24/7 emergency/trauma capability from trusted source. */
    VERIFIED,

    /** Hospital exists but emergency/trauma capability is unconfirmed by data source. */
    UNVERIFIED,

    /** Capability status is unknown or unspecified. */
    UNKNOWN
}

/**
 * Pure Kotlin domain model representing a prospective nearby hospital for emergency response.
 *
 * @param placeId Stable identifier from provider/source (e.g. Google Places ID).
 * @param name Official facility name.
 * @param address Physical postal address or location description.
 * @param latitude Geographic latitude coordinate.
 * @param longitude Geographic longitude coordinate.
 * @param distanceMeters Straight-line or route distance from rider location in meters, if computed.
 * @param phoneNumber Verified telephone number string, or null if unavailable.
 * @param emergencyCapabilityStatus Verification status of trauma/emergency care.
 * @param source Data source attribution (e.g. "Google Places", "Local Registry").
 * @param isOpenNow Current operating status if reliably provided by the data source.
 */
data class EmergencyHospital(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double? = null,
    val phoneNumber: String? = null,
    val emergencyCapabilityStatus: EmergencyCapabilityStatus = EmergencyCapabilityStatus.UNVERIFIED,
    val source: String = "Place Provider",
    val isOpenNow: Boolean? = null
) {
    /**
     * Google Maps navigation URL targeting the hospital's verified coordinates.
     * Note: This is separate and distinct from the rider's incident coordinates.
     */
    val mapsUrl: String
        get() = "https://maps.google.com/?q=$latitude,$longitude"
}
