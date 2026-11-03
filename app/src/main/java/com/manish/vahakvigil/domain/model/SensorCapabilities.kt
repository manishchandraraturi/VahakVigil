package com.manish.vahakvigil.domain.model

/**
 * Domain representation of available hardware sensors on the device.
 *
 * @param hasAccelerometer Whether a hardware accelerometer is present.
 * @param hasGyroscope Whether a hardware gyroscope is present.
 */
data class SensorCapabilities(
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false
) {
    /**
     * True if the minimal sensor requirements for motion monitoring are satisfied.
     * An accelerometer is mandatory; a gyroscope provides angular velocity enhancement.
     */
    val isSupported: Boolean
        get() = hasAccelerometer
}
