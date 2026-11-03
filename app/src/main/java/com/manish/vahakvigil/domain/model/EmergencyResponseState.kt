package com.manish.vahakvigil.domain.model

/**
 * Unified lifecycle state for the end-to-end emergency response process.
 *
 * Tracks the progression from initial location acquisition through hospital discovery,
 * hospital communication, ambulance coordination, and emergency contact dispatch.
 */
enum class EmergencyResponseState {
    /** No emergency active. */
    IDLE,

    /** Current GPS fix acquisition is in progress. */
    LOCATION_ACQUIRING,

    /** Current GPS coordinates acquired successfully. */
    LOCATION_AVAILABLE,

    /** GPS coordinates unavailable or timed out; proceeding with fallback. */
    LOCATION_UNAVAILABLE,

    /** Querying nearby hospital candidates based on location. */
    SEARCHING_HOSPITAL,

    /** Suitable verified hospital candidate found and ranked. */
    HOSPITAL_FOUND,

    /** Hospital search yielded no results or failed safely; continuing dispatch. */
    HOSPITAL_UNAVAILABLE,

    /** Hospital dialer/calling action initiated. */
    HOSPITAL_CALL_REQUESTED,

    /** Hospital communication could not be initiated or failed. */
    HOSPITAL_CALL_FAILED,

    /** Ambulance request submitted to external provider. */
    AMBULANCE_REQUESTED,

    /** External ambulance provider confirmed dispatch. */
    AMBULANCE_CONFIRMED,

    /** Ambulance provider rejected, unavailable, or unconfigured. */
    AMBULANCE_FAILED,

    /** Designated emergency contacts notified via SMS. */
    CONTACTS_NOTIFIED,

    /** Emergency response executed partially (e.g. contacts notified but hospital unavailable). */
    PARTIALLY_COMPLETED,

    /** All configured emergency response actions executed successfully. */
    COMPLETED,

    /** Complete failure of emergency dispatch pipeline. */
    FAILED
}
