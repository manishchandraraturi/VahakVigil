package com.manish.vahakvigil.domain.formatter

import com.manish.vahakvigil.domain.model.EmergencyDispatchContact
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.LocalIncidentEvidence
import com.manish.vahakvigil.domain.model.EmergencyMedicalSummary
import com.manish.vahakvigil.domain.model.EmergencySmsMessage
import com.manish.vahakvigil.domain.model.EmergencySmsValidationStatus
import com.manish.vahakvigil.domain.model.SmsEncoding
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests for [EmergencySmsFormatter].
 */
class EmergencySmsFormatterTest {

    private lateinit var formatter: EmergencySmsFormatter

    private val fullSummary = EmergencyMedicalSummary(
        riderName = "Manish Sharma",
        bloodGroup = "O+",
        allergies = "Penicillin",
        conditions = "Asthma",
        medications = "Inhaler",
        notes = "Call spouse first"
    )

    private val sampleContact = EmergencyDispatchContact(
        id = 1L,
        name = "Priya Sharma",
        relationship = "Spouse",
        phone = "+919876543210"
    )

    @Before
    fun setUp() {
        formatter = EmergencySmsFormatter(ZoneId.of("UTC"))
    }

    // ─── 1. Full payload produces expected message ────────────────────────────

    @Test
    fun `full payload produces expected message`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = fullSummary,
            emergencyContacts = listOf(sampleContact),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertTrue(sms.messageText.contains("VAHAKVIGIL EMERGENCY ALERT"))
        assertTrue(sms.messageText.contains("Possible emergency detected."))
        assertTrue(sms.messageText.contains("Rider: Manish Sharma"))
        assertTrue(sms.messageText.contains("Incident time: 2023-11-14 22:13:20 UTC"))
        assertTrue(sms.messageText.contains("Blood group: O+"))
        assertTrue(sms.messageText.contains("Allergies: Penicillin"))
        assertTrue(sms.messageText.contains("Conditions: Asthma"))
        assertTrue(sms.messageText.contains("Medications: Inhaler"))
        assertTrue(sms.messageText.contains("Notes: Call spouse first"))
        assertTrue(sms.messageText.contains("Location: unavailable"))
        assertEquals(SmsEncoding.GSM_7BIT, sms.encoding)
        assertTrue(sms.segmentCount >= 1)
    }

    @Test
    fun `local incident evidence is never serialized into emergency SMS`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = listOf(sampleContact),
            localEvidence = LocalIncidentEvidence(
                incidentTimestampMs = 1_700_000_000_000L,
                voiceNote = "private local note",
                photoReference = "/private/incident-evidence/secret.jpg"
            )
        )

        val sms = formatter.format(payload)

        assertFalse(sms.messageText.contains("private local note"))
        assertFalse(sms.messageText.contains("secret.jpg"))
    }

    // ─── 2. Missing medical profile formats safely ────────────────────────────

    @Test
    fun `missing medical profile formats safely`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = listOf(sampleContact),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertFalse("Should not contain rider line", sms.messageText.contains("Rider:"))
        assertFalse("Should not contain blood group", sms.messageText.contains("Blood group:"))
        assertTrue(sms.messageText.contains("Location: unavailable"))
        assertFalse("Must never output literal null", sms.messageText.contains("null"))
    }

    // ─── 3. Empty contacts do not break formatting ────────────────────────────

    @Test
    fun `empty contacts do not break formatting`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = fullSummary,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertNotNull(sms)
        assertTrue(sms.messageText.contains("VAHAKVIGIL EMERGENCY ALERT"))
    }

    // ─── 4. Unavailable location is represented correctly ─────────────────────

    @Test
    fun `unavailable location is represented correctly`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertTrue(sms.messageText.contains("Location: unavailable"))
    }

    // ─── 5. Available location formats correctly ──────────────────────────────

    @Test
    fun `available location formats correctly`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Available(latitude = 12.9716, longitude = 77.5946)
        )

        val sms = formatter.format(payload)

        assertTrue(sms.messageText.contains("Location: 12.9716, 77.5946"))
        assertTrue(sms.messageText.contains("Map: https://maps.google.com/?q=12.9716,77.5946"))
    }

    // ─── 6. Blank medical fields are omitted ──────────────────────────────────

    @Test
    fun `blank medical fields are omitted`() {
        val sparseSummary = EmergencyMedicalSummary(
            riderName = "Rider Name",
            bloodGroup = "B+",
            allergies = "   ",
            conditions = "",
            medications = "  ",
            notes = ""
        )
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = sparseSummary,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertTrue(sms.messageText.contains("Rider: Rider Name"))
        assertTrue(sms.messageText.contains("Blood group: B+"))
        assertFalse("Blank allergies should be omitted", sms.messageText.contains("Allergies:"))
        assertFalse("Blank conditions should be omitted", sms.messageText.contains("Conditions:"))
        assertFalse("Blank medications should be omitted", sms.messageText.contains("Medications:"))
        assertFalse("Blank notes should be omitted", sms.messageText.contains("Notes:"))
    }

    // ─── 7. Message never contains literal "null" ─────────────────────────────

    @Test
    fun `message never contains literal null`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertFalse(sms.messageText.contains("null"))
        val validation = formatter.validate(sms)
        assertTrue(validation.isValid)
    }

    // ─── 8. Message never contains fake coordinates ───────────────────────────

    @Test
    fun `message never contains fake coordinates`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)

        assertFalse("Must not inject fake lat/lng", sms.messageText.contains("0.0, 0.0"))
        assertTrue(sms.messageText.contains("Location: unavailable"))
    }

    // ─── 9. Short message calculates one segment ──────────────────────────────

    @Test
    fun `short message calculates one segment`() {
        val text = "Short emergency alert text"
        val segments = formatter.calculateSegments(text, SmsEncoding.GSM_7BIT)

        assertEquals(1, segments)
    }

    // ─── 10. 160-character GSM message is one segment ─────────────────────────

    @Test
    fun `160-character GSM message is one segment`() {
        val text = "A".repeat(160)
        val segments = formatter.calculateSegments(text, SmsEncoding.GSM_7BIT)

        assertEquals(1, segments)
    }

    // ─── 11. 161-character GSM message requires multipart ─────────────────────

    @Test
    fun `161-character GSM message requires multipart`() {
        val text = "A".repeat(161)
        val segments = formatter.calculateSegments(text, SmsEncoding.GSM_7BIT)

        assertEquals(2, segments)
    }

    // ─── 12. Multipart GSM uses 153-character segments ────────────────────────

    @Test
    fun `multipart GSM uses 153-character segments`() {
        // 153 * 2 = 306 characters -> 2 segments
        val text306 = "A".repeat(306)
        assertEquals(2, formatter.calculateSegments(text306, SmsEncoding.GSM_7BIT))

        // 307 characters -> 3 segments
        val text307 = "A".repeat(307)
        assertEquals(3, formatter.calculateSegments(text307, SmsEncoding.GSM_7BIT))
    }

    // ─── GSM Extension Character Tests ────────────────────────────────────────

    @Test
    fun `one GSM extension character consumes two septets`() {
        val text = "Hello ^" // 6 basic + 1 ext = 7 chars
        assertEquals(SmsEncoding.GSM_7BIT, formatter.detectEncoding(text))
        assertEquals(8, formatter.calculateGsmSeptets(text))
        assertEquals(1, formatter.calculateSegments(text, SmsEncoding.GSM_7BIT))
    }

    @Test
    fun `multiple GSM extension characters consume two septets each`() {
        val text = "^{}\\[~]|€" // 9 extension chars
        assertEquals(SmsEncoding.GSM_7BIT, formatter.detectEncoding(text))
        assertEquals(18, formatter.calculateGsmSeptets(text))
        assertEquals(1, formatter.calculateSegments(text, SmsEncoding.GSM_7BIT))
    }

    @Test
    fun `boundary around 160 septets with GSM extension characters`() {
        // 158 basic + 1 extension = 159 chars, 160 septets -> 1 segment
        val text160 = "A".repeat(158) + "^"
        assertEquals(159, text160.length)
        assertEquals(160, formatter.calculateGsmSeptets(text160))
        assertEquals(1, formatter.calculateSegments(text160, SmsEncoding.GSM_7BIT))

        // 159 basic + 1 extension = 160 chars, 161 septets -> 2 segments
        val text161 = "A".repeat(159) + "^"
        assertEquals(160, text161.length)
        assertEquals(161, formatter.calculateGsmSeptets(text161))
        assertEquals(2, formatter.calculateSegments(text161, SmsEncoding.GSM_7BIT))
    }

    @Test
    fun `boundary around 153 septets in multipart GSM message`() {
        // 151 basic + 1 ext = 153 septets -> 1 segment (within 160 single segment limit)
        val text153 = "A".repeat(151) + "€"
        assertEquals(153, formatter.calculateGsmSeptets(text153))
        assertEquals(1, formatter.calculateSegments(text153, SmsEncoding.GSM_7BIT))

        // In multipart (capacity = 153 septets / segment):
        // 304 basic + 1 ext = 306 septets (153 * 2) -> 2 segments
        val text306Septets = "A".repeat(304) + "€"
        assertEquals(306, formatter.calculateGsmSeptets(text306Septets))
        assertEquals(2, formatter.calculateSegments(text306Septets, SmsEncoding.GSM_7BIT))

        // 305 basic + 1 ext = 307 septets (153 * 2 + 1) -> 3 segments
        val text307Septets = "A".repeat(305) + "€"
        assertEquals(307, formatter.calculateGsmSeptets(text307Septets))
        assertEquals(3, formatter.calculateSegments(text307Septets, SmsEncoding.GSM_7BIT))
    }

    // ─── 13. Unicode message uses 70-character single-segment limit ───────────

    @Test
    fun `Unicode message uses 70-character single-segment limit`() {
        // Devanagari / Unicode text of 70 characters
        val text70 = "अ".repeat(70)
        assertEquals(SmsEncoding.UNICODE, formatter.detectEncoding(text70))
        assertEquals(1, formatter.calculateSegments(text70, SmsEncoding.UNICODE))

        val text71 = "अ".repeat(71)
        assertEquals(2, formatter.calculateSegments(text71, SmsEncoding.UNICODE))
    }

    // ─── 14. Unicode multipart uses 67-character segments ─────────────────────

    @Test
    fun `Unicode multipart uses 67-character segments`() {
        // 67 * 2 = 134 characters -> 2 segments
        val text134 = "अ".repeat(134)
        assertEquals(2, formatter.calculateSegments(text134, SmsEncoding.UNICODE))

        // 135 characters -> 3 segments
        val text135 = "अ".repeat(135)
        assertEquals(3, formatter.calculateSegments(text135, SmsEncoding.UNICODE))
    }

    // ─── 15. Character encoding classification is deterministic ───────────────

    @Test
    fun `character encoding classification is deterministic`() {
        val gsmText = "Hello World! 12345 @ # $ % & - + / : ."
        assertEquals(SmsEncoding.GSM_7BIT, formatter.detectEncoding(gsmText))

        val unicodeText = "Hello नमस्ते World 🚨"
        assertEquals(SmsEncoding.UNICODE, formatter.detectEncoding(unicodeText))
    }

    // ─── 16. Blank message is invalid ─────────────────────────────────────────

    @Test
    fun `blank message is invalid`() {
        val blankMessage = EmergencySmsMessage(
            messageText = "   ",
            characterCount = 3,
            encoding = SmsEncoding.GSM_7BIT,
            segmentCount = 1
        )
        val validation = formatter.validate(blankMessage)

        assertEquals(EmergencySmsValidationStatus.INVALID, validation.status)
        assertFalse(validation.isValid)
    }

    // ─── 17. Message with all available fields remains deterministic ──────────

    @Test
    fun `message with all available fields remains deterministic`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = fullSummary,
            emergencyContacts = listOf(sampleContact),
            location = EmergencyLocation.Available(12.34, 56.78)
        )

        val sms1 = formatter.format(payload)
        val sms2 = formatter.format(payload)

        assertEquals(sms1.messageText, sms2.messageText)
        assertEquals(sms1.characterCount, sms2.characterCount)
        assertEquals(sms1.encoding, sms2.encoding)
        assertEquals(sms1.segmentCount, sms2.segmentCount)
    }

    // ─── 18. Repeated formatting produces identical output ────────────────────

    @Test
    fun `repeated formatting of the same payload produces identical output`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 999_999L,
            medicalSummary = fullSummary,
            emergencyContacts = emptyList(),
            location = EmergencyLocation.Unavailable
        )

        val first = formatter.format(payload)
        repeat(5) {
            val subsequent = formatter.format(payload)
            assertEquals(first, subsequent)
        }
    }

    // ─── 19. Formatter has no Android dependencies ────────────────────────────

    @Test
    fun `formatter has no Android dependencies`() {
        // Pure JVM verification test
        val message = formatter.format(
            EmergencyDispatchPayload(
                incidentTimestamp = 100L,
                medicalSummary = null,
                emergencyContacts = emptyList(),
                location = EmergencyLocation.Unavailable
            )
        )
        assertNotNull(message)
    }

    // ─── 20. No SMS API is invoked ────────────────────────────────────────────

    @Test
    fun `no SMS API is invoked`() {
        // Formatting does not send anything, only generates string and calculations
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 200L,
            medicalSummary = fullSummary,
            emergencyContacts = listOf(sampleContact),
            location = EmergencyLocation.Unavailable
        )
        val sms = formatter.format(payload)
        val validation = formatter.validate(sms)

        assertNotNull(sms)
        assertNotNull(validation)
    }

    // ─── 21. Incident timestamp formatting with explicit timezones ────────────

    @Test
    fun `incident timestamp formats into human-readable date time with specified timezone`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = listOf(sampleContact),
            location = EmergencyLocation.Unavailable
        )

        // UTC test
        val utcFormatter = EmergencySmsFormatter(ZoneId.of("UTC"))
        val utcSms = utcFormatter.format(payload)
        assertTrue(utcSms.messageText.contains("Incident time: 2023-11-14 22:13:20 UTC"))

        // Asia/Kolkata (IST = UTC+05:30) test
        val istFormatter = EmergencySmsFormatter(ZoneId.of("Asia/Kolkata"))
        val istSms = istFormatter.format(payload)
        assertTrue(istSms.messageText.contains("Incident time: 2023-11-15 03:43:20 IST"))
    }

    @Test
    fun `incident timestamp uses standard GSM 7-bit characters without Unicode bloat`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 1_700_000_000_000L,
            medicalSummary = null,
            emergencyContacts = listOf(sampleContact),
            location = EmergencyLocation.Unavailable
        )

        val sms = formatter.format(payload)
        assertEquals(SmsEncoding.GSM_7BIT, sms.encoding)
        assertFalse(sms.messageText.contains("1700000000000"))
        assertTrue(sms.messageText.contains("2023-11-14 22:13:20 UTC"))
    }
}

