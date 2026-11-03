package com.manish.vahakvigil.domain.orchestration

import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencySmsValidationStatus
import com.manish.vahakvigil.domain.sms.EmergencySmsSender
import kotlinx.coroutines.runBlocking

/**
 * [EmergencyActionExecutor] implementation that dispatches emergency SMS alerts
 * to compiled emergency contacts via an injected [EmergencySmsSender].
 *
 * Responsibilities:
 * - Validates that the request action type is [EmergencyActionType.SMS].
 * - Validates that a compiled [com.manish.vahakvigil.domain.model.EmergencyDispatchPayload] exists.
 * - Validates that at least one emergency contact is present.
 * - Formats and validates the emergency SMS message via [EmergencySmsFormatter].
 * - Dispatches SMS to each contact independently (never grouping multiple phone numbers).
 * - Aggregates individual dispatch results into a unified [EmergencyActionResult].
 */
class SmsEmergencyActionExecutor(
    private val smsSender: EmergencySmsSender,
    private val smsFormatter: EmergencySmsFormatter = EmergencySmsFormatter()
) : EmergencyActionExecutor {

    override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
        // Step 1: Validate action type
        if (request.actionType != EmergencyActionType.SMS) {
            return EmergencyActionResult.notTriggered(
                actionType = request.actionType,
                timestamp = request.incidentTimestamp,
                reason = "Action type is not SMS: ${request.actionType}"
            )
        }

        // Step 2: Validate dispatch payload presence
        val payload = request.dispatchPayload
        if (payload == null) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "Missing dispatch payload"
            )
        }

        // Step 3: Validate emergency contacts
        if (payload.emergencyContacts.isEmpty()) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "No emergency contacts available in payload"
            )
        }

        // Step 4: Format and validate SMS message
        val smsMessage = smsFormatter.format(payload)
        val validation = smsFormatter.validate(smsMessage)
        if (validation.status == EmergencySmsValidationStatus.INVALID) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "Invalid emergency SMS: ${validation.reason}"
            )
        }

        if (smsMessage.messageText.isBlank()) {
            return EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "Formatted emergency SMS text is blank"
            )
        }

        // Step 5: Dispatch to each contact independently
        var triggeredCount = 0
        var failedCount = 0
        val failureReasons = mutableListOf<String>()

        for (contact in payload.emergencyContacts) {
            val phone = contact.phone.trim()
            if (phone.isBlank()) {
                failedCount++
                failureReasons.add("Blank phone number for contact id ${contact.id}")
                continue
            }

            val sendResult = runBlocking {
                smsSender.send(
                    phoneNumber = phone,
                    message = smsMessage.messageText
                )
            }

            if (sendResult.status == EmergencyActionResultStatus.TRIGGERED) {
                triggeredCount++
            } else {
                failedCount++
                failureReasons.add(sendResult.details)
            }
        }

        return if (triggeredCount > 0 && failedCount == 0) {
            EmergencyActionResult(
                status = EmergencyActionResultStatus.TRIGGERED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "SMS dispatch triggered for $triggeredCount contact(s)"
            )
        } else if (triggeredCount > 0 && failedCount > 0) {
            EmergencyActionResult(
                status = EmergencyActionResultStatus.TRIGGERED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "Partial SMS dispatch: $triggeredCount triggered, $failedCount failed (${failureReasons.joinToString("; ")})"
            )
        } else {
            EmergencyActionResult(
                status = EmergencyActionResultStatus.FAILED,
                actionType = EmergencyActionType.SMS,
                timestamp = request.incidentTimestamp,
                details = "SMS dispatch failed for all contacts: ${failureReasons.joinToString("; ")}"
            )
        }
    }
}
