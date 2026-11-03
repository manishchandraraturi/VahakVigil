package com.manish.vahakvigil.ui

import com.manish.vahakvigil.domain.model.RiderReadiness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderReadinessTest {
    @Test fun locationRequiresPermissionAndEnabledGps() {
        assertTrue(readiness(permission = true, gps = true).locationAvailable)
        assertFalse(readiness(permission = false, gps = true).locationAvailable)
        assertFalse(readiness(permission = true, gps = false).locationAvailable)
    }

    @Test fun sensorAvailabilityIsTheMonitoringHardwareRequirement() {
        assertTrue(readiness(sensor = true).monitoringRequirementsAvailable)
        assertFalse(readiness(sensor = false).monitoringRequirementsAvailable)
    }

    private fun readiness(sensor: Boolean = true, permission: Boolean = true, gps: Boolean = true) = RiderReadiness(
        sensorAvailable = sensor,
        locationPermissionGranted = permission,
        gpsEnabled = gps,
        notificationsAllowed = false,
        emergencyContactsConfigured = false,
        medicalProfileConfigured = false,
        backgroundMonitoringActive = false
    )
}
