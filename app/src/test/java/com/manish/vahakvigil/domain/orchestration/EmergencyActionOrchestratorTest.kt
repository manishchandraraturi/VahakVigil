package com.manish.vahakvigil.domain.orchestration

import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM tests for [EmergencyActionOrchestrator].
 */
class EmergencyActionOrchestratorTest {

    private lateinit var fakeExecutor: FakeEmergencyActionExecutor
    private lateinit var orchestrator: EmergencyActionOrchestrator

    @Before
    fun setUp() {
        fakeExecutor = FakeEmergencyActionExecutor()
        orchestrator = EmergencyActionOrchestrator(actionExecutor = fakeExecutor)
    }

    private fun noEmergencyDecision(timestamp: Long = 1000L) = EmergencyDecision(
        state = EmergencyDecisionState.NO_EMERGENCY,
        reason = "Normal motion",
        incidentTimestampMs = timestamp,
        postImpactStatus = null
    )

    private fun candidateDecision(timestamp: Long = 1000L) = EmergencyDecision(
        state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
        reason = "Potential incident followed by stillness",
        incidentTimestampMs = timestamp,
        postImpactStatus = null
    )

    // ─── 1. NO_EMERGENCY + IDLE → no action ───────────────────────────────────

    @Test
    fun `NO_EMERGENCY + IDLE produces NOT_TRIGGERED and does not execute action`() {
        val result = orchestrator.evaluate(
            decision = noEmergencyDecision(),
            countdownState = EmergencyCountdownState.IDLE
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ─── 2. NO_EMERGENCY + COUNTING_DOWN → no action ──────────────────────────

    @Test
    fun `NO_EMERGENCY + COUNTING_DOWN produces NOT_TRIGGERED and does not execute action`() {
        val result = orchestrator.evaluate(
            decision = noEmergencyDecision(),
            countdownState = EmergencyCountdownState.COUNTING_DOWN
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ─── 3. EMERGENCY_CANDIDATE + COUNTING_DOWN → no action ───────────────────

    @Test
    fun `EMERGENCY_CANDIDATE + COUNTING_DOWN produces NOT_TRIGGERED and does not execute action`() {
        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.COUNTING_DOWN
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ─── 4. EMERGENCY_CANDIDATE + CANCELLED → no action ───────────────────────

    @Test
    fun `EMERGENCY_CANDIDATE + CANCELLED produces NOT_TRIGGERED and does not execute action`() {
        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.CANCELLED
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ─── 5. EMERGENCY_CANDIDATE + EXPIRED → action executes ───────────────────

    @Test
    fun `EMERGENCY_CANDIDATE + EXPIRED executes action and produces TRIGGERED`() {
        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(1, fakeExecutor.callCount)
    }

    // ─── 6. EXPIRED action executes exactly once ──────────────────────────────

    @Test
    fun `EXPIRED action executes exactly once on repeated evaluation`() {
        val decision = candidateDecision(timestamp = 2000L)
        val result1 = orchestrator.evaluate(decision = decision, countdownState = EmergencyCountdownState.EXPIRED)
        val result2 = orchestrator.evaluate(decision = decision, countdownState = EmergencyCountdownState.EXPIRED)

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result1.status)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result2.status)
        assertEquals(1, fakeExecutor.callCount)
    }

    // ─── 7. Repeated EXPIRED updates do not duplicate execution ───────────────

    @Test
    fun `repeated EXPIRED updates do not duplicate execution`() {
        val decision = candidateDecision(timestamp = 3000L)
        repeat(5) {
            val result = orchestrator.evaluate(decision = decision, countdownState = EmergencyCountdownState.EXPIRED)
            assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        }

        assertEquals(1, fakeExecutor.callCount)
    }

    // ─── 8. Cancelled state never executes an action ──────────────────────────

    @Test
    fun `cancelled state prevents any subsequent action execution for the incident`() {
        val decision = candidateDecision(timestamp = 4000L)

        // First user cancels
        val cancelResult = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.CANCELLED
        )
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, cancelResult.status)

        // Then an EXPIRED update arrives for the same incident
        val expiredResult = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.EXPIRED
        )
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, expiredResult.status)
        assertEquals(0, fakeExecutor.callCount)
    }

    // ─── 9. New incident can execute independently ────────────────────────────

    @Test
    fun `new incident can execute independently`() {
        val incident1 = candidateDecision(timestamp = 1000L)
        val result1 = orchestrator.evaluate(decision = incident1, countdownState = EmergencyCountdownState.EXPIRED)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result1.status)
        assertEquals(1, fakeExecutor.callCount)

        // Distinct second incident
        val incident2 = candidateDecision(timestamp = 8000L)
        val result2 = orchestrator.evaluate(decision = incident2, countdownState = EmergencyCountdownState.EXPIRED)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result2.status)
        assertEquals(2, fakeExecutor.callCount)
    }

    // ─── 10. Executor failure produces FAILED ─────────────────────────────────

    @Test
    fun `executor failure produces FAILED status`() {
        fakeExecutor.outcomeStatus = EmergencyActionResultStatus.FAILED

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED
        )

        assertEquals(EmergencyActionResultStatus.FAILED, result.status)
        assertEquals(1, fakeExecutor.callCount)
    }

    @Test
    fun `failed dispatch does not mark incident as dispatched and allows subsequent successful attempt`() {
        fakeExecutor.outcomeStatus = EmergencyActionResultStatus.FAILED
        val decision = candidateDecision(timestamp = 9999L)

        // First attempt fails
        val result1 = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.EXPIRED
        )
        assertEquals(EmergencyActionResultStatus.FAILED, result1.status)
        assertEquals(1, fakeExecutor.callCount)

        // Executor now succeeds (e.g. transient issue resolved)
        fakeExecutor.outcomeStatus = EmergencyActionResultStatus.TRIGGERED
        val result2 = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.EXPIRED
        )
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result2.status)
        assertEquals(2, fakeExecutor.callCount)

        // Subsequent call for the same incident is blocked by duplicate prevention
        val result3 = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.EXPIRED
        )
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result3.status)
        assertEquals(2, fakeExecutor.callCount)
    }

    // ─── 11. Successful executor produces TRIGGERED ───────────────────────────

    @Test
    fun `successful executor produces TRIGGERED status`() {
        fakeExecutor.outcomeStatus = EmergencyActionResultStatus.TRIGGERED

        val result = orchestrator.evaluate(
            decision = candidateDecision(),
            countdownState = EmergencyCountdownState.EXPIRED
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(1, fakeExecutor.callCount)
    }

    // ─── 12. Action request contains correct incident context ─────────────────

    @Test
    fun `action request contains correct incident context`() {
        val decision = candidateDecision(timestamp = 5500L)
        orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS
        )

        val captured = fakeExecutor.lastCapturedRequest
        assertNotNull(captured)
        assertEquals(5500L, captured!!.incidentTimestamp)
        assertEquals(EmergencyActionType.SMS, captured.actionType)
        assertEquals(decision.reason, captured.reason)
    }

    // ─── Fake Executor Helper ─────────────────────────────────────────────────

    private class FakeEmergencyActionExecutor(
        var outcomeStatus: EmergencyActionResultStatus = EmergencyActionResultStatus.TRIGGERED
    ) : EmergencyActionExecutor {
        var callCount: Int = 0
        var lastCapturedRequest: EmergencyActionRequest? = null

        override fun execute(request: EmergencyActionRequest): EmergencyActionResult {
            callCount++
            lastCapturedRequest = request
            return EmergencyActionResult(
                status = outcomeStatus,
                actionType = request.actionType,
                timestamp = request.incidentTimestamp,
                details = if (outcomeStatus == EmergencyActionResultStatus.FAILED) "Dispatch failed" else "Dispatch successful"
            )
        }
    }
}
