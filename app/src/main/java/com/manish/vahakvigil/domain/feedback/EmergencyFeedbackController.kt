package com.manish.vahakvigil.domain.feedback

/**
 * Platform-independent abstraction for emergency multi-sensory alerting feedback
 * (vibration, alarm sound, screen brightness, and beacon flashlight).
 *
 * Decoupled from Android hardware and window manager APIs.
 */
interface EmergencyFeedbackController {

    /**
     * Activates emergency multi-sensory alerts (initiating vibration pattern,
     * sounding alarm, requesting maximum screen brightness, and engaging beacon flashlight).
     */
    fun startAlert()

    /**
     * Immediately deactivates all active emergency alerts and restores default device state.
     */
    fun stopAlert()
}
