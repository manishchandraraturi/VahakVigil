package com.manish.vahakvigil.ui

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.model.NotificationReadinessPolicy
import com.manish.vahakvigil.domain.model.ReadinessCheckStatus
import com.manish.vahakvigil.domain.model.RiderReadiness
import com.manish.vahakvigil.domain.model.RiderReadinessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderReadinessTest {
    @Test
    fun allConfiguredChecksAreReadyAndMonitoringReflectsActualState() {
        val readiness = RiderReadiness.from(snapshot(backgroundMonitoringActive = true))

        assertEquals(ReadinessCheckStatus.READY, readiness.overallStatus)
        assertEquals(ReadinessCheckStatus.READY, readiness.accelerometerStatus)
        assertEquals(ReadinessCheckStatus.READY, readiness.gyroscopeStatus)
        assertEquals(ReadinessCheckStatus.READY, readiness.contactsStatus)
        assertEquals(ReadinessCheckStatus.READY, readiness.locationStatus)
        assertEquals(ReadinessCheckStatus.READY, readiness.notificationsStatus)
        assertEquals(ReadinessCheckStatus.READY, readiness.medicalProfileStatus)
        assertTrue(readiness.isMonitoringActive)
    }

    @Test
    fun contactIsReadyOnlyWhenAtLeastOneNamedPhoneNumberIsValid() {
        val valid = Contact(name = "Rider contact", relationship = "Friend", phone = "+919876543210")
        val invalid = Contact(name = "", relationship = "Friend", phone = "not-a-number")

        assertEquals(1, RiderReadiness.countValidEmergencyContacts(listOf(invalid, valid)))
        assertEquals(0, RiderReadiness.countValidEmergencyContacts(listOf(invalid)))
        assertEquals(ReadinessCheckStatus.NEEDS_ATTENTION, RiderReadiness.from(snapshot(validEmergencyContactCount = 0)).contactsStatus)
    }

    @Test
    fun medicalProfileNeedsInformationBeyondName() {
        assertFalse(MedicalProfile(name = "Rider").hasMeaningfulEmergencyInformation)
        assertTrue(MedicalProfile(name = "Rider", allergies = "No known allergies").hasMeaningfulEmergencyInformation)
        assertTrue(MedicalProfile(age = "35").hasMeaningfulEmergencyInformation)
        assertEquals(ReadinessCheckStatus.NEEDS_ATTENTION, RiderReadiness.from(snapshot(medicalProfileConfigured = false)).medicalProfileStatus)
    }

    @Test
    fun deniedLocationPermissionAndDisabledLocationNeedAttention() {
        val denied = RiderReadiness.from(snapshot(locationPermissionGranted = false, gpsEnabled = true))
        val disabled = RiderReadiness.from(snapshot(locationPermissionGranted = true, gpsEnabled = false))

        assertEquals(ReadinessCheckStatus.NEEDS_ATTENTION, denied.locationStatus)
        assertFalse(denied.locationAvailable)
        assertEquals(ReadinessCheckStatus.NEEDS_ATTENTION, disabled.locationStatus)
        assertFalse(disabled.locationAvailable)
    }

    @Test
    fun notificationPermissionFollowsAndroidVersionRules() {
        assertTrue(NotificationReadinessPolicy.isAllowed(apiLevel = 26, runtimePermissionGranted = false))
        assertTrue(NotificationReadinessPolicy.isAllowed(apiLevel = 32, runtimePermissionGranted = false))
        assertFalse(NotificationReadinessPolicy.isAllowed(apiLevel = 33, runtimePermissionGranted = false))
        assertTrue(NotificationReadinessPolicy.isAllowed(apiLevel = 33, runtimePermissionGranted = true))
    }

    @Test
    fun missingAccelerometerIsUnavailableWhileGyroscopeIsReportedSeparately() {
        val noSensors = RiderReadiness.from(snapshot(hasAccelerometer = false, hasGyroscope = false))
        val accelerometerOnly = RiderReadiness.from(snapshot(hasAccelerometer = true, hasGyroscope = false))

        assertEquals(ReadinessCheckStatus.UNAVAILABLE, noSensors.accelerometerStatus)
        assertEquals(ReadinessCheckStatus.UNAVAILABLE, noSensors.overallStatus)
        assertEquals(ReadinessCheckStatus.UNAVAILABLE, accelerometerOnly.gyroscopeStatus)
        assertTrue(accelerometerOnly.monitoringRequirementsAvailable)
    }

    @Test
    fun recomputingSnapshotAfterReturningFromSettingsReflectsNewPermissions() {
        val beforeSettings = RiderReadiness.from(snapshot(locationPermissionGranted = false, notificationsAllowed = false))
        val afterSettings = RiderReadiness.from(
            snapshot(locationPermissionGranted = true, notificationsAllowed = true)
        )

        assertEquals(ReadinessCheckStatus.NEEDS_ATTENTION, beforeSettings.locationStatus)
        assertEquals(ReadinessCheckStatus.NEEDS_ATTENTION, beforeSettings.notificationsStatus)
        assertEquals(ReadinessCheckStatus.READY, afterSettings.locationStatus)
        assertEquals(ReadinessCheckStatus.READY, afterSettings.notificationsStatus)
    }

    private fun snapshot(
        hasAccelerometer: Boolean = true,
        hasGyroscope: Boolean = true,
        locationPermissionGranted: Boolean = true,
        gpsEnabled: Boolean = true,
        notificationsAllowed: Boolean = true,
        validEmergencyContactCount: Int = 1,
        medicalProfileConfigured: Boolean = true,
        backgroundMonitoringActive: Boolean = false
    ) = RiderReadinessSnapshot(
        hasAccelerometer = hasAccelerometer,
        hasGyroscope = hasGyroscope,
        locationPermissionGranted = locationPermissionGranted,
        gpsEnabled = gpsEnabled,
        notificationsAllowed = notificationsAllowed,
        validEmergencyContactCount = validEmergencyContactCount,
        medicalProfileConfigured = medicalProfileConfigured,
        backgroundMonitoringActive = backgroundMonitoringActive
    )
}
