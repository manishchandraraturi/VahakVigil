package com.manish.vahakvigil.sms

import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.sms.EmergencySmsSender

data class SentSmsRecord(
    val phoneNumber: String,
    val message: String
)

/**
 * Pure JVM in-memory fake implementation of [EmergencySmsSender] for testing.
 */
class FakeEmergencySmsSender(
    var outcome: EmergencyActionResultStatus = EmergencyActionResultStatus.TRIGGERED,
    var shouldSimulatePermissionFailure: Boolean = false,
    var shouldSimulateProviderFailure: Boolean = false,
    var failurePhonePredicate: ((String) -> Boolean)? = null
) : EmergencySmsSender {

    val sentRecords = mutableListOf<SentSmsRecord>()
    var callCount: Int = 0
        private set

    override suspend fun send(
        phoneNumber: String,
        message: String
    ): EmergencyActionResult {
        callCount++

        val trimmedPhone = phoneNumber.trim()
        if (trimmedPhone.isBlank()) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = System.currentTimeMillis(),
                details = "Phone number is blank"
            )
        }

        if (message.isBlank()) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = System.currentTimeMillis(),
                details = "SMS message text is blank"
            )
        }

        if (shouldSimulatePermissionFailure) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = System.currentTimeMillis(),
                details = "SEND_SMS permission not granted"
            )
        }

        if (shouldSimulateProviderFailure) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = System.currentTimeMillis(),
                details = "SmsManager failure simulated"
            )
        }

        if (failurePhonePredicate?.invoke(trimmedPhone) == true) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = System.currentTimeMillis(),
                details = "Recipient transmission failed"
            )
        }

        sentRecords.add(SentSmsRecord(trimmedPhone, message))

        return EmergencyActionResult(
            status = outcome,
            actionType = EmergencyActionType.SMS,
            timestamp = System.currentTimeMillis(),
            details = if (outcome == EmergencyActionResultStatus.TRIGGERED) "SMS send initiated" else "SMS send failed"
        )
    }

    fun reset() {
        sentRecords.clear()
        callCount = 0
        outcome = EmergencyActionResultStatus.TRIGGERED
        shouldSimulatePermissionFailure = false
        shouldSimulateProviderFailure = false
        failurePhonePredicate = null
    }
}
