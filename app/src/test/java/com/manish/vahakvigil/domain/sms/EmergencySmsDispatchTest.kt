package com.manish.vahakvigil.domain.sms

import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyDispatchContact
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyMedicalSummary
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.orchestration.SmsEmergencyActionExecutor
import com.manish.vahakvigil.sms.FakeEmergencySmsSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive pure JVM unit tests for Emergency SMS Dispatch Execution Foundation.
 */
class EmergencySmsDispatchTest {

    private lateinit var fakeSmsSender: FakeEmergencySmsSender
    private lateinit var smsExecutor: SmsEmergencyActionExecutor
    private lateinit var orchestrator: EmergencyActionOrchestrator
    private lateinit var formatter: EmergencySmsFormatter

    private val sampleContact1 = EmergencyDispatchContact(
        id = 1L,
        name = "Primary Contact",
        relationship = "Spouse",
        phone = "+919876543210"
    )

    private val sampleContact2 = EmergencyDispatchContact(
        id = 2L,
        name = "Secondary Contact",
        relationship = "Sibling",
        phone = "+919876543211"
    )

    private val sampleMedicalSummary = EmergencyMedicalSummary(
        riderName = "Test Rider",
        bloodGroup = "O+",
        allergies = "None",
        conditions = "None",
        medications = "None",
        notes = "Safe rider"
    )

    private val validPayload = EmergencyDispatchPayload(
        incidentTimestamp = 1_000_000L,
        medicalSummary = sampleMedicalSummary,
        emergencyContacts = listOf(sampleContact1),
        location = EmergencyLocation.Available(12.9716, 77.5946)
    )

    @Before
    fun setUp() {
        fakeSmsSender = FakeEmergencySmsSender()
        formatter = EmergencySmsFormatter()
        smsExecutor = SmsEmergencyActionExecutor(fakeSmsSender, formatter)
        orchestrator = EmergencyActionOrchestrator(actionExecutor = smsExecutor)
    }

    private fun candidateDecision(timestamp: Long = 1_000_000L) = EmergencyDecision(
        state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
        reason = "Candidate incident with sustained stillness",
        incidentTimestampMs = timestamp,
        postImpactStatus = null
    )

    private fun noEmergencyDecision(timestamp: Long = 1_000_000L) = EmergencyDecision(
        state = EmergencyDecisionState.NO_EMERGENCY,
        reason = "Normal motion",
        incidentTimestampMs = timestamp,
        postImpactStatus = null
    )

    // ─── 1. Valid authorized dispatch reaches sender ──────────────────────────

    @Test
    fun `valid authorized dispatch reaches sender`() {
        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(1, fakeSmsSender.callCount)
        assertEquals(1, fakeSmsSender.sentRecords.size)
        assertEquals("+919876543210", fakeSmsSender.sentRecords.first().phoneNumber)
        assertTrue(fakeSmsSender.sentRecords.first().message.contains("VAHAKVIGIL EMERGENCY ALERT"))
    }

    // ─── 2. Unauthorized dispatch does not reach sender ───────────────────────

    @Test
    fun `unauthorized dispatch does not reach sender`() {
        val result = orchestrator.evaluate(
            decision = noEmergencyDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeSmsSender.callCount)
        assertTrue(fakeSmsSender.sentRecords.isEmpty())
    }

    // ─── 3. Cancelled incident does not send ──────────────────────────────────

    @Test
    fun `cancelled incident does not send`() {
        // Initial cancellation
        val cancelResult = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.CANCELLED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, cancelResult.status)
        assertEquals(0, fakeSmsSender.callCount)

        // Subsequent expired check on same incident
        val postCancelResult = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, postCancelResult.status)
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 4. Expired countdown is required ─────────────────────────────────────

    @Test
    fun `expired countdown is required`() {
        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.COUNTING_DOWN,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 5. Non-expired countdown does not send ───────────────────────────────

    @Test
    fun `non-expired countdown does not send`() {
        val resultIdle = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.IDLE,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, resultIdle.status)
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 6. Duplicate incident does not send twice ────────────────────────────

    @Test
    fun `duplicate incident does not send twice`() {
        val firstResult = orchestrator.evaluate(
            decision = candidateDecision(timestamp = 5000L),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload.copy(incidentTimestamp = 5000L)
        )
        assertEquals(EmergencyActionResultStatus.TRIGGERED, firstResult.status)
        assertEquals(1, fakeSmsSender.callCount)

        // Second evaluation of the same incident
        val secondResult = orchestrator.evaluate(
            decision = candidateDecision(timestamp = 5000L),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload.copy(incidentTimestamp = 5000L)
        )
        assertEquals(firstResult, secondResult)
        assertEquals(1, fakeSmsSender.callCount)
    }

    // ─── 7. Blank phone number fails safely ───────────────────────────────────

    @Test
    fun `blank phone number fails safely`() {
        val blankPhoneContact = EmergencyDispatchContact(1L, "Blank", "Friend", "   ")
        val blankPhonePayload = validPayload.copy(emergencyContacts = listOf(blankPhoneContact))

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = blankPhonePayload
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertTrue(result.details.contains("Blank phone number"))
    }

    // ─── 8. Blank message fails safely ────────────────────────────────────────

    @Test
    fun `blank message fails safely`() = runTest {
        val result = fakeSmsSender.send("+919876543210", "   ")

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertTrue(result.details.contains("blank"))
    }

    // ─── 9. Missing dispatch payload fails safely ─────────────────────────────

    @Test
    fun `missing dispatch payload fails safely`() {
        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = null
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertTrue(result.details.contains("Missing dispatch payload"))
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 10. Multiple contacts are handled independently ──────────────────────

    @Test
    fun `multiple contacts are handled independently`() {
        val multiContactPayload = validPayload.copy(
            emergencyContacts = listOf(sampleContact1, sampleContact2)
        )

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = multiContactPayload
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(2, fakeSmsSender.callCount)
        assertEquals(2, fakeSmsSender.sentRecords.size)
        assertEquals("+919876543210", fakeSmsSender.sentRecords[0].phoneNumber)
        assertEquals("+919876543211", fakeSmsSender.sentRecords[1].phoneNumber)
    }

    // ─── 11. Sender failure produces FAILED ───────────────────────────────────

    @Test
    fun `sender failure produces FAILED`() {
        fakeSmsSender.outcome = EmergencyActionResultStatus.FAILED

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
    }

    // ─── 12. Sender success produces TRIGGERED ────────────────────────────────

    @Test
    fun `sender success produces TRIGGERED`() {
        fakeSmsSender.outcome = EmergencyActionResultStatus.TRIGGERED

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertTrue(result.details.contains("SMS request accepted by Android messaging system"))
    }

    // ─── 13. No SMS is sent during normal sensor monitoring ───────────────────

    @Test
    fun `no SMS is sent during normal sensor monitoring`() {
        val result = orchestrator.evaluate(
            decision = noEmergencyDecision(),
            countdownState = EmergencyCountdownState.IDLE,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 14. No SMS is sent merely because GPS becomes available ──────────────

    @Test
    fun `no SMS is sent merely because GPS becomes available`() {
        // Location is available, but motion state is normal
        val payloadWithLocation = validPayload.copy(
            location = EmergencyLocation.Available(28.6139, 77.2090, 5.0f, 12345L)
        )

        val result = orchestrator.evaluate(
            decision = noEmergencyDecision(),
            countdownState = EmergencyCountdownState.IDLE,
            dispatchPayload = payloadWithLocation
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 15. Formatter validation is respected ────────────────────────────────

    @Test
    fun `formatter validation is respected`() {
        // Empty contacts payload causes validation/safety failure before transmission
        val emptyContactsPayload = validPayload.copy(emergencyContacts = emptyList())

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = emptyContactsPayload
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertTrue(result.details.contains("No emergency contacts"))
        assertEquals(0, fakeSmsSender.callCount)
    }

    // ─── 16. Fake sender produces deterministic results ───────────────────────

    @Test
    fun `fake sender produces deterministic results`() = runTest {
        val res1 = fakeSmsSender.send("+919876543210", "Test message")
        val res2 = fakeSmsSender.send("+919876543210", "Test message")

        assertEquals(res1.status, res2.status)
        assertEquals(res1.actionType, res2.actionType)
        assertEquals(2, fakeSmsSender.callCount)
    }

    // ─── 17. Permission failure in sender produces FAILED safely ──────────────

    @Test
    fun `permission failure in sender produces FAILED safely`() = runTest {
        fakeSmsSender.shouldSimulatePermissionFailure = true

        val result = fakeSmsSender.send("+919876543210", "Test message")

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertTrue(result.details.contains("permission"))
    }

    // ─── 18. Partial multi-contact success produces TRIGGERED ─────────────────

    @Test
    fun `partial multi-contact success produces TRIGGERED with count details`() {
        val multiPayload = validPayload.copy(
            emergencyContacts = listOf(sampleContact1, sampleContact2)
        )
        // Simulate failure for contact 2 only
        fakeSmsSender.failurePhonePredicate = { it == sampleContact2.phone }

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = multiPayload
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(2, fakeSmsSender.callCount)
        assertTrue(result.details.contains("Partial SMS dispatch"))
        assertTrue(result.details.contains("1 accepted, 1 failed"))
        // Privacy check: raw phone number of contact 2 must not be exposed in result details
        assertFalse(result.details.contains(sampleContact2.phone))
    }

    // ─── 19. All contacts failing produces FAILED ─────────────────────────────

    @Test
    fun `all contacts failing produces FAILED without falsely reporting success`() {
        val multiPayload = validPayload.copy(
            emergencyContacts = listOf(sampleContact1, sampleContact2)
        )
        fakeSmsSender.outcome = EmergencyActionResultStatus.FAILED

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = multiPayload
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertEquals(2, fakeSmsSender.callCount)
        assertTrue(result.details.contains("SMS dispatch failed for all contacts"))
    }

    @Test
    fun `ambiguous sender outcome is preserved and must not be treated as a safe retry`() {
        fakeSmsSender.outcome = EmergencyActionResultStatus.FAILED
        fakeSmsSender.deliveryStatusOverride = com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = validPayload
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertEquals(com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus.DELIVERY_UNKNOWN, result.deliveryStatus)
        assertTrue(result.details.contains("automatic retry is unsafe"))
        assertEquals(1, fakeSmsSender.callCount)
    }

    // ─── 20. Privacy - phone numbers not in aggregate failure details ─────────

    @Test
    fun `privacy - phone numbers not leaked in aggregate failure details`() {
        val multiPayload = validPayload.copy(
            emergencyContacts = listOf(sampleContact1, sampleContact2)
        )
        fakeSmsSender.outcome = EmergencyActionResultStatus.FAILED

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = multiPayload
        )

        assertFalse(result.details.contains(sampleContact1.phone))
        assertFalse(result.details.contains(sampleContact2.phone))
    }
}
