package com.manish.vahakvigil.domain.model

/** Derived readiness rules shared by the setup UI and unit tests. */
data class RiderReadiness(
    val sensorAvailable: Boolean,
    val locationPermissionGranted: Boolean,
    val gpsEnabled: Boolean,
    val notificationsAllowed: Boolean,
    val emergencyContactsConfigured: Boolean,
    val medicalProfileConfigured: Boolean,
    val backgroundMonitoringActive: Boolean
) {
    val locationAvailable: Boolean get() = locationPermissionGranted && gpsEnabled
    val monitoringRequirementsAvailable: Boolean get() = sensorAvailable
    val isMonitoringActive: Boolean get() = backgroundMonitoringActive
}
