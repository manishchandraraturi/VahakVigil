package com.manish.vahakvigil.domain.formatter

import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyLocationSource
import com.manish.vahakvigil.domain.model.EmergencySmsMessage
import com.manish.vahakvigil.domain.model.EmergencySmsValidationResult
import com.manish.vahakvigil.domain.model.SmsEncoding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure Kotlin formatter and validator for emergency dispatch SMS messages.
 *
 * Converts an [EmergencyDispatchPayload] into an [EmergencySmsMessage] following
 * standard GSM 03.38 / Unicode telecom encoding and multi-part segment boundaries.
 *
 * Formats incident timestamps using [zoneId] (defaults to [ZoneId.systemDefault]).
 */
class EmergencySmsFormatter(
    private val zoneId: ZoneId = try { ZoneId.systemDefault() } catch (_: Exception) { ZoneId.of("UTC") }
) {

    companion object {
        const val GSM_SINGLE_SEGMENT_LIMIT = 160
        const val GSM_MULTIPART_SEGMENT_LIMIT = 153
        const val UNICODE_SINGLE_SEGMENT_LIMIT = 70
        const val UNICODE_MULTIPART_SEGMENT_LIMIT = 67

        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US)

        private const val GSM_BASIC_CHARS =
            "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        private const val GSM_EXTENDED_CHARS = "^{}\\[~]|€\u000c"
    }

    /**
     * Formats an [EmergencyDispatchPayload] into an [EmergencySmsMessage].
     *
     * @param payload The compiled emergency dispatch data.
     * @return Formatted and analyzed [EmergencySmsMessage].
     */
    fun format(payload: EmergencyDispatchPayload): EmergencySmsMessage {
        val lines = mutableListOf<String>()
        lines.add("VAHAKVIGIL EMERGENCY ALERT")
        lines.add("Possible emergency detected.")

        val summary = payload.medicalSummary
        if (summary != null && summary.riderName.isNotBlank()) {
            lines.add("Rider: ${summary.riderName.trim()}")
        }

        val formattedTime = formatTimestamp(payload.incidentTimestamp)
        lines.add("Incident time: $formattedTime")

        if (summary != null) {
            if (summary.bloodGroup.isNotBlank()) {
                lines.add("Blood group: ${summary.bloodGroup.trim()}")
            }
            if (summary.allergies.isNotBlank()) {
                lines.add("Allergies: ${summary.allergies.trim()}")
            }
            if (summary.conditions.isNotBlank()) {
                lines.add("Conditions: ${summary.conditions.trim()}")
            }
            if (summary.medications.isNotBlank()) {
                lines.add("Medications: ${summary.medications.trim()}")
            }
            if (summary.notes.isNotBlank()) {
                lines.add("Notes: ${summary.notes.trim()}")
            }
        }

        when (val loc = payload.location) {
            is EmergencyLocation.Unavailable -> lines.add("Location: unavailable")
            is EmergencyLocation.Available -> {
                lines.add("Location: ${loc.latitude}, ${loc.longitude}")
                if (loc.source == EmergencyLocationSource.LAST_KNOWN) {
                    lines.add("Location source: Last known location${loc.timestampMs?.let { " at ${formatTimestamp(it)}" }.orEmpty()}")
                } else {
                    lines.add("Location source: Current location fix")
                }
                lines.add("Map: ${loc.mapsUrl}")
            }
        }

        if (payload.hospital != null) {
            lines.add("Emergency hospital: ${payload.hospital.name}")
            if (!payload.hospital.phoneNumber.isNullOrBlank()) {
                lines.add("Hospital: ${payload.hospital.phoneNumber}")
            }
        }

        val text = lines.joinToString("\n")
        val encoding = detectEncoding(text)
        val charCount = text.length
        val segments = calculateSegments(text, encoding)

        return EmergencySmsMessage(
            messageText = text,
            characterCount = charCount,
            encoding = encoding,
            segmentCount = segments
        )
    }

    /**
     * Detects whether the text can be encoded in standard GSM 7-bit, or requires Unicode (UCS-2).
     */
    fun detectEncoding(text: String): SmsEncoding {
        for (ch in text) {
            if (ch !in GSM_BASIC_CHARS && ch !in GSM_EXTENDED_CHARS) {
                return SmsEncoding.UNICODE
            }
        }
        return SmsEncoding.GSM_7BIT
    }

    /**
     * Calculates the number of GSM septets consumed by [text].
     * Basic GSM-7 characters consume 1 septet; characters in the GSM extension table
     * consume 2 septets (due to the 0x1B escape prefix).
     */
    fun calculateGsmSeptets(text: String): Int {
        var septets = 0
        for (ch in text) {
            septets += if (ch in GSM_EXTENDED_CHARS) 2 else 1
        }
        return septets
    }

    /**
     * Calculates the deterministic telecom segment count for a given text and encoding.
     */
    fun calculateSegments(text: String, encoding: SmsEncoding): Int {
        if (text.isEmpty()) return 0

        return when (encoding) {
            SmsEncoding.GSM_7BIT -> {
                val septets = calculateGsmSeptets(text)
                if (septets <= GSM_SINGLE_SEGMENT_LIMIT) {
                    1
                } else {
                    (septets + GSM_MULTIPART_SEGMENT_LIMIT - 1) / GSM_MULTIPART_SEGMENT_LIMIT
                }
            }
            SmsEncoding.UNICODE -> {
                val length = text.length
                if (length <= UNICODE_SINGLE_SEGMENT_LIMIT) {
                    1
                } else {
                    (length + UNICODE_MULTIPART_SEGMENT_LIMIT - 1) / UNICODE_MULTIPART_SEGMENT_LIMIT
                }
            }
        }
    }

    /**
     * Formats an epoch millisecond timestamp into a human-readable string using [targetZoneId].
     */
    fun formatTimestamp(epochMillis: Long, targetZoneId: ZoneId = zoneId): String {
        return try {
            val instant = Instant.ofEpochMilli(epochMillis)
            instant.atZone(targetZoneId).format(TIMESTAMP_FORMATTER)
        } catch (_: Exception) {
            epochMillis.toString()
        }
    }

    /**
     * Validates the generated [EmergencySmsMessage].
     */
    fun validate(message: EmergencySmsMessage): EmergencySmsValidationResult {
        if (message.messageText.isBlank()) {
            return EmergencySmsValidationResult.invalid("Message text is blank")
        }
        if (message.messageText.contains("null")) {
            return EmergencySmsValidationResult.invalid("Message text contains literal 'null'")
        }
        if (message.segmentCount <= 0) {
            return EmergencySmsValidationResult.invalid("Invalid segment count: ${message.segmentCount}")
        }
        if (message.segmentCount > 1) {
            return EmergencySmsValidationResult.requiresMultipart(message.segmentCount)
        }
        return EmergencySmsValidationResult.valid()
    }
}
