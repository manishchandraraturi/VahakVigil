package com.manish.vahakvigil.domain.sms

import com.manish.vahakvigil.domain.model.EmergencyActionResult

/**
 * Pure Kotlin abstraction for sending emergency SMS messages.
 *
 * Independent of Android framework classes (no SmsManager, Context, or Telephony APIs).
 */
interface EmergencySmsSender {

    /**
     * Dispatches an SMS message to [phoneNumber].
     *
     * @param phoneNumber The destination telephone number.
     * @param message The SMS text payload.
     * @return [EmergencyActionResult] indicating whether the transmission was initiated
     *         ([com.manish.vahakvigil.domain.model.EmergencyActionResultStatus.TRIGGERED]) or
     *         failed ([com.manish.vahakvigil.domain.model.EmergencyActionResultStatus.FAILED]).
     */
    suspend fun send(
        phoneNumber: String,
        message: String
    ): EmergencyActionResult
}
