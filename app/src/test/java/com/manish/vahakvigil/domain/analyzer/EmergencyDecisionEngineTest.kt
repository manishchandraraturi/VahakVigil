package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.PostImpactMotionResult
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.QualifiedIncident
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [EmergencyDecisionEngine].
 */
class EmergencyDecisionEngineTest {

    private val defaultConfig = EmergencyDecisionConfig.DEFAULT
    private val engine = EmergencyDecisionEngine(defaultConfig)

    private fun createIncident(endMs: Long = 2000L): QualifiedIncident {
        val temporalEvent = TemporalMotionEvent(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            windowStartMs = endMs - 500L,
            windowEndMs = endMs,
            peakAccelerationMagnitude = 30f,
            peakGyroscopeMagnitude = 4f,
            readingCount = 5
        )
        val candidate = CandidateAccidentEvent(
            windowStartMs = endMs - 500L,
            windowEndMs = endMs,
            confidenceScore = ConfidenceScore.fromRaw(0.8f),
            temporalEvent = temporalEvent,
            readingCount = 5
        )
        return QualifiedIncident(
            windowStartMs = endMs - 500L,
            windowEndMs = endMs,
            qualifyingConfidenceScore = ConfidenceScore.fromRaw(0.8f),
            qualifyingWindowCount = 1,
            candidateEvents = listOf(candidate)
        )
    }

    private fun createPostImpactResult(
        status: PostImpactStatus,
        stillnessDurationMs: Long = if (status == PostImpactStatus.STILL) 2000L else 0L
    ) = PostImpactMotionResult(
        status = status,
        observationStartMs = 2100L,
        observationEndMs = 4100L,
        stillnessDurationMs = stillnessDurationMs,
        peakAccelerationMagnitude = 9.8f,
        peakGyroscopeMagnitude = 0.1f,
        readingCount = 5
    )

    // ─── 1. No QualifiedIncident → NO_EMERGENCY ───────────────────────────────

    @Test
    fun `no QualifiedIncident yields NO_EMERGENCY`() {
        val postImpact = createPostImpactResult(PostImpactStatus.STILL)
        val decision = engine.evaluate(incident = null, postImpactResult = postImpact)

        assertEquals(EmergencyDecisionState.NO_EMERGENCY, decision.state)
        assertTrue(decision.reason.contains("No qualified incident"))
    }

    // ─── 2. No PostImpactMotionResult → NO_EMERGENCY ──────────────────────────

    @Test
    fun `no PostImpactMotionResult yields NO_EMERGENCY`() {
        val incident = createIncident()
        val decision = engine.evaluate(incident = incident, postImpactResult = null)

        assertEquals(EmergencyDecisionState.NO_EMERGENCY, decision.state)
        assertTrue(decision.reason.contains("No post-impact motion result"))
    }

    // ─── 3. QualifiedIncident + OBSERVING → NO_EMERGENCY ──────────────────────

    @Test
    fun `QualifiedIncident with OBSERVING post-impact status yields NO_EMERGENCY`() {
        val incident = createIncident()
        val postImpact = createPostImpactResult(PostImpactStatus.OBSERVING)
        val decision = engine.evaluate(incident = incident, postImpactResult = postImpact)

        assertEquals(EmergencyDecisionState.NO_EMERGENCY, decision.state)
        assertTrue(decision.reason.contains("not STILL"))
    }

    // ─── 4. QualifiedIncident + MOVING → NO_EMERGENCY ─────────────────────────

    @Test
    fun `QualifiedIncident with MOVING post-impact status yields NO_EMERGENCY`() {
        val incident = createIncident()
        val postImpact = createPostImpactResult(PostImpactStatus.MOVING)
        val decision = engine.evaluate(incident = incident, postImpactResult = postImpact)

        assertEquals(EmergencyDecisionState.NO_EMERGENCY, decision.state)
        assertTrue(decision.reason.contains("not STILL"))
    }

    // ─── 5. QualifiedIncident + STILL → EMERGENCY_CANDIDATE ───────────────────

    @Test
    fun `QualifiedIncident with STILL post-impact status yields EMERGENCY_CANDIDATE`() {
        val incident = createIncident(endMs = 2000L)
        val postImpact = createPostImpactResult(PostImpactStatus.STILL)
        val decision = engine.evaluate(incident = incident, postImpactResult = postImpact)

        assertEquals(EmergencyDecisionState.EMERGENCY_CANDIDATE, decision.state)
        assertEquals(2000L, decision.incidentTimestampMs)
        assertEquals(PostImpactStatus.STILL, decision.postImpactStatus)
    }

    // ─── 6. Decision reason is deterministic ──────────────────────────────────

    @Test
    fun `decision reasons are deterministic and explanatory`() {
        val incident = createIncident()
        val stillResult = createPostImpactResult(PostImpactStatus.STILL)
        val movingResult = createPostImpactResult(PostImpactStatus.MOVING)

        val candidateDecision = engine.evaluate(incident, stillResult)
        assertEquals("Qualified incident confirmed with sustained post-impact stillness", candidateDecision.reason)

        val noEmergencyDecision = engine.evaluate(incident, movingResult)
        assertEquals("Post-impact motion state is MOVING, not STILL", noEmergencyDecision.reason)
    }

    // ─── 7. Configuration is respected ────────────────────────────────────────

    @Test
    fun `custom config allowing emergency without stillness is respected`() {
        val lenientConfig = EmergencyDecisionConfig(
            requireQualifiedIncident = true,
            requireStillPostImpact = false
        )
        val lenientEngine = EmergencyDecisionEngine(lenientConfig)

        val incident = createIncident()
        val movingResult = createPostImpactResult(PostImpactStatus.MOVING)

        val decision = lenientEngine.evaluate(incident, movingResult)
        assertEquals(EmergencyDecisionState.EMERGENCY_CANDIDATE, decision.state)
    }
}
