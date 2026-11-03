package com.manish.vahakvigil.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.sms.EmergencySmsSender

/**
 * Android platform implementation of [EmergencySmsSender] using [SmsManager].
 *
 * Responsibilities & Safety guarantees:
 * - Checks [Manifest.permission.SEND_SMS]. If not granted, safely returns [EmergencyActionResultStatus.FAILED]
 *   without crashing or triggering telephony.
 * - Validates that [phoneNumber] and [message] are non-blank.
 * - Handles multipart SMS via [SmsManager.divideMessage] and [SmsManager.sendMultipartTextMessage]
 *   when parts > 1, or [SmsManager.sendTextMessage] when parts == 1.
 * - Does NOT implement delivery BroadcastReceivers in this milestone (represents initiation as TRIGGERED).
 * - Catches any [SecurityException] or runtime exception and safely returns [EmergencyActionResultStatus.FAILED].
 * - Strictly respects privacy: NEVER logs phone numbers or SMS message contents.
 */
class AndroidEmergencySmsSender(
    private val context: Context,
    private val smsManagerProvider: () -> SmsManager? = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
) : EmergencySmsSender {

    override suspend fun send(
        phoneNumber: String,
        message: String
    ): EmergencyActionResult {
        val timestamp = System.currentTimeMillis()

        // Step 1: Input validations
        val trimmedPhone = phoneNumber.trim()
        if (trimmedPhone.isBlank()) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "Phone number is blank"
            )
        }

        if (message.isBlank()) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "SMS message text is blank"
            )
        }

        // Step 2: Permission check
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "SEND_SMS permission not granted"
            )
        }

        // Step 3: Telephony dispatch
        return try {
            val smsManager = smsManagerProvider() ?: return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "SmsManager unavailable"
            )

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    trimmedPhone,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    trimmedPhone,
                    null,
                    message,
                    null,
                    null
                )
            }

            EmergencyActionResult(
                status = EmergencyActionResultStatus.TRIGGERED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "SMS transmission initiated (${parts.size} segment(s))"
            )
        } catch (e: SecurityException) {
            EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "SecurityException during SMS transmission: ${e.message}"
            )
        } catch (e: Exception) {
            EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = timestamp,
                details = "Failed to send SMS: ${e.message}"
            )
        }
    }
}
