package com.manish.vahakvigil.domain.model

/**
 * Pure Kotlin domain model representing a formatted emergency SMS message.
 *
 * @param messageText The full text body of the emergency SMS.
 * @param characterCount Total character length of the message text.
 * @param encoding Detected encoding classification ([SmsEncoding.GSM_7BIT] or [SmsEncoding.UNICODE]).
 * @param segmentCount Estimated number of SMS transmission segments required based on standard telecom specs.
 */
data class EmergencySmsMessage(
    val messageText: String,
    val characterCount: Int,
    val encoding: SmsEncoding,
    val segmentCount: Int
)
