package com.manish.vahakvigil.domain.model

/**
 * Delivery status acknowledging transmission initiation vs unknown carrier delivery.
 */
enum class EmergencyDeliveryStatus {
    NOT_REQUESTED,
    SMS_REQUESTED,
    SENT_TO_MESSAGING_SYSTEM,
    DELIVERY_UNKNOWN,
    FAILED
}

/**
 * Outcome status of an emergency action evaluation or execution.
 */
enum class EmergencyActionResultStatus {
    NOT_TRIGGERED,
    TRIGGERED,
    FAILED
}

/**
 * Pure Kotlin domain model representing the result of an emergency action dispatch.
 *
 * @param status Status of the action dispatch.
 * @param actionType The prospective action type.
 * @param timestamp Timestamp of the evaluation/execution.
 * @param details Contextual details or failure reason for debugging and logging.
 * @param deliveryStatus Granular delivery status representing transmission without fabricating carrier receipts.
 */
data class EmergencyActionResult(
    val status: EmergencyActionResultStatus,
    val actionType: EmergencyActionType,
    val timestamp: Long,
    val details: String = "",
    val deliveryStatus: EmergencyDeliveryStatus = when (status) {
        EmergencyActionResultStatus.NOT_TRIGGERED -> EmergencyDeliveryStatus.NOT_REQUESTED
        EmergencyActionResultStatus.TRIGGERED -> EmergencyDeliveryStatus.DELIVERY_UNKNOWN
        EmergencyActionResultStatus.FAILED -> EmergencyDeliveryStatus.FAILED
    }
) {
    companion object {
        fun notTriggered(
            actionType: EmergencyActionType = EmergencyActionType.SMS,
            timestamp: Long = 0L,
            reason: String = ""
        ): EmergencyActionResult = EmergencyActionResult(
            status = EmergencyActionResultStatus.NOT_TRIGGERED,
            actionType = actionType,
            timestamp = timestamp,
            details = reason,
            deliveryStatus = EmergencyDeliveryStatus.NOT_REQUESTED
        )
    }
}
