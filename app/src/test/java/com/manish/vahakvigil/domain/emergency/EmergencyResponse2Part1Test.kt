package com.manish.vahakvigil.domain.emergency

import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.orchestration.SmsEmergencyActionExecutor
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import com.manish.vahakvigil.sms.FakeEmergencySmsSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification test suite for Emergency Response 2.0 — Part 1.
 *
 * Covers:
 * 1. Emergency contacts (max 3, priority 1/2/3, reordering, deletion normalization).
 * 2. Medical profile integration (populated fields only, safe fallbacks).
 * 3. GPS acquisition (coordinates, Google Maps link, unavailable fallback).
 * 4. State tracking & delivery status (no fabricated delivery receipts).
 * 5. Failure-safe orchestration & cancelled incident protection.
 */
class EmergencyResponse2Part1Test {

    private lateinit var contactRepository: FakeContactRepository
    private lateinit var medicalRepository: FakeMedicalProfileRepository
    private lateinit var dataCompiler: EmergencyDispatchDataCompiler
    private lateinit var smsFormatter: EmergencySmsFormatter
    private lateinit var fakeSmsSender: FakeEmergencySmsSender
    private lateinit var actionExecutor: SmsEmergencyActionExecutor
    private lateinit var orchestrator: EmergencyActionOrchestrator

    private val sampleMedical = MedicalProfile(
        name = "Vikram Patel",
        age = "32",
        bloodGroup = "B+",
        conditions = "Hypertension",
        allergies = "None",
        medications = "Amlodipine",
        height = "180",
        weight = "78",
        notes = "Contact primary first"
    )

    @Before
    fun setUp() {
        contactRepository = FakeContactRepository()
        medicalRepository = FakeMedicalProfileRepository(sampleMedical)
        dataCompiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)
        smsFormatter = EmergencySmsFormatter()
        fakeSmsSender = FakeEmergencySmsSender()
        actionExecutor = SmsEmergencyActionExecutor(fakeSmsSender, smsFormatter)
        orchestrator = EmergencyActionOrchestrator(actionExecutor)
    }

    // ─── 1. Contacts Limit & Priorities ───────────────────────────────────────

    @Test
    fun `contacts are strictly ordered by explicit priority`() = runTest {
        val c2 = Contact(id = 2L, name = "Secondary", relationship = "Brother", phone = "+919876543212", priority = 2)
        val c1 = Contact(id = 1L, name = "Primary", relationship = "Spouse", phone = "+919876543211", priority = 1)
        val c3 = Contact(id = 3L, name = "Tertiary", relationship = "Friend", phone = "+919876543213", priority = 3)

        contactRepository.saveContact(c2)
        contactRepository.saveContact(c1)
        contactRepository.saveContact(c3)

        val retrieved = contactRepository.getContacts().first()
        assertEquals(3, retrieved.size)
        assertEquals("Primary", retrieved[0].name)
        assertEquals(1, retrieved[0].priority)
        assertEquals("Secondary", retrieved[1].name)
        assertEquals(2, retrieved[1].priority)
        assertEquals("Tertiary", retrieved[2].name)
        assertEquals(3, retrieved[2].priority)
    }

    @Test
    fun `reordering contacts updates priorities and compilation order`() = runTest {
        val c1 = Contact(id = 1L, name = "Old Primary", relationship = "Spouse", phone = "+919876543211", priority = 1)
        val c2 = Contact(id = 2L, name = "Old Secondary", relationship = "Brother", phone = "+919876543212", priority = 2)
        contactRepository.saveContact(c1)
        contactRepository.saveContact(c2)

        // Swap priorities
        contactRepository.updatePriorities(
            listOf(
                c2.copy(priority = 1),
                c1.copy(priority = 2)
            )
        )

        val payload = dataCompiler.compile(incidentTimestamp = 1000L)
        assertEquals(2, payload.emergencyContacts.size)
        assertEquals("Old Secondary", payload.emergencyContacts[0].name)
        assertEquals(1, payload.emergencyContacts[0].priority)
        assertEquals("Old Primary", payload.emergencyContacts[1].name)
        assertEquals(2, payload.emergencyContacts[1].priority)
    }

    @Test
    fun `deleting contact normalizes remaining priorities to consecutive 1 to N`() = runTest {
        val c1 = Contact(id = 1L, name = "C1", relationship = "R1", phone = "111", priority = 1)
        val c2 = Contact(id = 2L, name = "C2", relationship = "R2", phone = "222", priority = 2)
        val c3 = Contact(id = 3L, name = "C3", relationship = "R3", phone = "333", priority = 3)
        contactRepository.saveContact(c1)
        contactRepository.saveContact(c2)
        contactRepository.saveContact(c3)

        contactRepository.deleteContact(c2)

        val remaining = contactRepository.getContacts().first()
        assertEquals(2, remaining.size)
        assertEquals(1L, remaining[0].id)
        assertEquals(1, remaining[0].priority)
        assertEquals(3L, remaining[1].id)
        assertEquals(2, remaining[1].priority) // Formerly 3, normalized to 2
    }

    // ─── 2. Medical Profile Integration ───────────────────────────────────────

    @Test
    fun `medical profile populated fields are cleanly included in SMS`() = runTest {
        val contact = Contact(id = 1L, name = "Primary", relationship = "Spouse", phone = "+919876543211", priority = 1)
        contactRepository.saveContact(contact)

        val payload = dataCompiler.compile(incidentTimestamp = 12345678L)
        val sms = smsFormatter.format(payload)

        assertTrue(sms.messageText.contains("Rider: Vikram Patel"))
        assertTrue(sms.messageText.contains("Blood group: B+"))
        assertTrue(sms.messageText.contains("Conditions: Hypertension"))
        assertTrue(sms.messageText.contains("Allergies: None"))
        assertTrue(sms.messageText.contains("Medications: Amlodipine"))
        assertTrue(sms.messageText.contains("Notes: Contact primary first"))
        assertFalse("Must never output literal null", sms.messageText.contains("null"))
    }

    @Test
    fun `missing medical profile omits medical lines gracefully`() = runTest {
        val emptyMedical = FakeMedicalProfileRepository(initialProfile = null)
        val compiler = EmergencyDispatchDataCompiler(emptyMedical, contactRepository)
        val contact = Contact(id = 1L, name = "Primary", relationship = "Spouse", phone = "+919876543211", priority = 1)
        contactRepository.saveContact(contact)

        val payload = compiler.compile(incidentTimestamp = 12345678L)
        assertNull(payload.medicalSummary)

        val sms = smsFormatter.format(payload)
        assertFalse(sms.messageText.contains("Rider:"))
        assertFalse(sms.messageText.contains("Blood group:"))
        assertFalse(sms.messageText.contains("null"))
        assertTrue(sms.messageText.contains("VAHAKVIGIL EMERGENCY ALERT"))
    }

    // ─── 3. GPS Acquisition & Formatting ──────────────────────────────────────

    @Test
    fun `available GPS location formats latitude longitude and Google Maps URL`() = runTest {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 12345678L,
            medicalSummary = null,
            emergencyContacts = listOf(),
            location = EmergencyLocation.Available(latitude = 19.0760, longitude = 72.8777)
        )

        val sms = smsFormatter.format(payload)
        assertTrue(sms.messageText.contains("Location: 19.076, 72.8777"))
        assertTrue(sms.messageText.contains("Map: https://maps.google.com/?q=19.076,72.8777"))
    }

    @Test
    fun `unavailable GPS location formats fallback without crashing`() = runTest {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 12345678L,
            medicalSummary = null,
            emergencyContacts = listOf(),
            location = EmergencyLocation.Unavailable
        )

        val sms = smsFormatter.format(payload)
        assertTrue(sms.messageText.contains("Location: unavailable"))
        assertFalse(sms.messageText.contains("Map:"))
    }

    // ─── 4. Dispatch Delivery Status Semantics ────────────────────────────────

    @Test
    fun `successful dispatch initiation acknowledges transmission and marks delivery unknown`() = runTest {
        val c1 = Contact(id = 1L, name = "Primary", relationship = "Spouse", phone = "+919876543211", priority = 1)
        contactRepository.saveContact(c1)

        val payload = dataCompiler.compile(incidentTimestamp = 12345L)
        val request = EmergencyActionRequest(
            incidentTimestamp = 12345L,
            actionType = EmergencyActionType.SMS,
            reason = "Countdown expired",
            dispatchPayload = payload
        )

        val result = actionExecutor.execute(request)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        // Must acknowledge transmission without claiming delivery
        assertEquals(EmergencyDeliveryStatus.DELIVERY_UNKNOWN, result.deliveryStatus)
        assertEquals(1, fakeSmsSender.sentRecords.size)
    }

    @Test
    fun `dispatch fails gracefully when emergency contacts are empty`() = runTest {
        val payload = dataCompiler.compile(incidentTimestamp = 12345L)
        val request = EmergencyActionRequest(
            incidentTimestamp = 12345L,
            actionType = EmergencyActionType.SMS,
            reason = "Countdown expired",
            dispatchPayload = payload
        )

        val result = actionExecutor.execute(request)
        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertEquals(EmergencyDeliveryStatus.FAILED, result.deliveryStatus)
        assertEquals(0, fakeSmsSender.sentRecords.size)
    }

    // ─── 5. Stale / Cancelled Incident Protection ─────────────────────────────

    @Test
    fun `cancelled countdown never triggers emergency actions even on subsequent calls`() = runTest {
        val c1 = Contact(id = 1L, name = "Primary", relationship = "Spouse", phone = "+919876543211", priority = 1)
        contactRepository.saveContact(c1)
        val payload = dataCompiler.compile(incidentTimestamp = 99999L)

        val decision = EmergencyDecision(
            state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
            reason = "Impact detected",
            incidentTimestampMs = 99999L,
            postImpactStatus = null
        )

        // User cancels countdown (e.g. presses I'M FINE)
        val cancelResult = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.CANCELLED,
            dispatchPayload = payload
        )
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, cancelResult.status)
        assertEquals(0, fakeSmsSender.sentRecords.size)

        // Late update arrives with EXPIRED state for the same cancelled incident
        val lateResult = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.EXPIRED,
            dispatchPayload = payload
        )
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, lateResult.status)
        assertEquals("Incident was previously cancelled", lateResult.details)
        assertEquals(0, fakeSmsSender.sentRecords.size)
    }
}
