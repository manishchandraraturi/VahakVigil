package com.manish.vahakvigil.domain.model

import com.manish.vahakvigil.domain.validation.PhoneNumberValidator
import com.manish.vahakvigil.domain.validation.PhoneValidationResult

/** Input snapshot for one readiness refresh. Values are read from repositories and the Android device. */
data class RiderReadinessSnapshot(
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val locationPermissionGranted: Boolean,
    val gpsEnabled: Boolean,
    val notificationsAllowed: Boolean,
    val validEmergencyContactCount: Int,
    val medicalProfileConfigured: Boolean,
    val backgroundMonitoringActive: Boolean
)

enum class ReadinessCheckStatus { READY, NEEDS_ATTENTION, UNAVAILABLE }

/** Derived readiness rules shared by the dashboard and unit tests. */
data class RiderReadiness(
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val locationPermissionGranted: Boolean,
    val gpsEnabled: Boolean,
    val notificationsAllowed: Boolean,
    val validEmergencyContactCount: Int,
    val medicalProfileConfigured: Boolean,
    val backgroundMonitoringActive: Boolean
) {
    init {
        require(validEmergencyContactCount >= 0)
    }

    val sensorAvailable: Boolean get() = hasAccelerometer
    val emergencyContactsConfigured: Boolean get() = validEmergencyContactCount > 0
    val locationAvailable: Boolean get() = locationPermissionGranted && gpsEnabled
    val monitoringRequirementsAvailable: Boolean get() = sensorAvailable
    val isMonitoringActive: Boolean get() = backgroundMonitoringActive

    val accelerometerStatus: ReadinessCheckStatus
        get() = if (hasAccelerometer) ReadinessCheckStatus.READY else ReadinessCheckStatus.UNAVAILABLE
    val gyroscopeStatus: ReadinessCheckStatus
        get() = if (hasGyroscope) ReadinessCheckStatus.READY else ReadinessCheckStatus.UNAVAILABLE
    val contactsStatus: ReadinessCheckStatus
        get() = if (emergencyContactsConfigured) ReadinessCheckStatus.READY else ReadinessCheckStatus.NEEDS_ATTENTION
    val locationStatus: ReadinessCheckStatus
        get() = if (locationAvailable) ReadinessCheckStatus.READY else ReadinessCheckStatus.NEEDS_ATTENTION
    val notificationsStatus: ReadinessCheckStatus
        get() = if (notificationsAllowed) ReadinessCheckStatus.READY else ReadinessCheckStatus.NEEDS_ATTENTION
    val medicalProfileStatus: ReadinessCheckStatus
        get() = if (medicalProfileConfigured) ReadinessCheckStatus.READY else ReadinessCheckStatus.NEEDS_ATTENTION

    /** Core setup excludes optional gyro hardware and the inactive-until-ride monitoring state. */
    val overallStatus: ReadinessCheckStatus
        get() = when {
            !hasAccelerometer -> ReadinessCheckStatus.UNAVAILABLE
            !emergencyContactsConfigured || !locationAvailable || !notificationsAllowed || !medicalProfileConfigured -> ReadinessCheckStatus.NEEDS_ATTENTION
            else -> ReadinessCheckStatus.READY
        }

    companion object {
        fun countValidEmergencyContacts(contacts: List<Contact>): Int = contacts.count { contact ->
            contact.name.isNotBlank() && PhoneNumberValidator.validate(contact.phone) is PhoneValidationResult.Valid
        }

        fun from(snapshot: RiderReadinessSnapshot) = RiderReadiness(
            hasAccelerometer = snapshot.hasAccelerometer,
            hasGyroscope = snapshot.hasGyroscope,
            locationPermissionGranted = snapshot.locationPermissionGranted,
            gpsEnabled = snapshot.gpsEnabled,
            notificationsAllowed = snapshot.notificationsAllowed,
            validEmergencyContactCount = snapshot.validEmergencyContactCount,
            medicalProfileConfigured = snapshot.medicalProfileConfigured,
            backgroundMonitoringActive = snapshot.backgroundMonitoringActive
        )
    }
}

/** Android 13+ requires a runtime notification permission; earlier supported releases do not. */
object NotificationReadinessPolicy {
    const val ANDROID_13_API_LEVEL = 33

    fun isAllowed(apiLevel: Int, runtimePermissionGranted: Boolean): Boolean =
        apiLevel < ANDROID_13_API_LEVEL || runtimePermissionGranted
}
