package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.CandidateEventWindow
import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [EventQualificationEngine].
 *
 * All tests use explicit deterministic timestamps and mock domain models.
 * No real clocks, delays, or Android dependencies are used.
 */
class EventQualificationEngineTest {

    private val defaultConfig = EventQualificationConfig(
        minimumConfidenceScore = 0.60f,
        requiredConsecutiveWindows = 3,
        qualificationWindowMillis = 2000L
    )
    private val engine = EventQualificationEngine(defaultConfig)

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun createCandidate(
        score: Float = 0.70f,
        startMs: Long = 1000L,
        endMs: Long = 1200L,
        readingCount: Int = 5
    ): CandidateAccidentEvent {
        val temporalEvent = TemporalMotionEvent(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            windowStartMs = startMs,
            windowEndMs = endMs,
            peakAccelerationMagnitude = 30f,
            peakGyroscopeMagnitude = 4f,
            readingCount = readingCount
        )
        return CandidateAccidentEvent(
            windowStartMs = startMs,
            windowEndMs = endMs,
            confidenceScore = ConfidenceScore.fromRaw(score),
            temporalEvent = temporalEvent,
            readingCount = readingCount
        )
    }

    // ─── 1. Empty candidate window does not qualify ───────────────────────────

    @Test
    fun `empty candidate window does not qualify`() {
        val window = CandidateEventWindow(emptyList())
        val result = engine.evaluate(window)
        assertNull(result)

        val processResult = engine.process(null)
        assertNull(processResult)
    }

    // ─── 2. Candidate below threshold does not qualify ────────────────────────

    @Test
    fun `candidate below threshold does not qualify`() {
        val lowScoreCandidate = createCandidate(score = 0.50f) // below 0.60
        val result = engine.process(lowScoreCandidate)
        assertNull("Candidate below minimum confidence threshold must not qualify", result)
    }

    // ─── 3. One qualifying candidate does not qualify when multiple required ──

    @Test
    fun `one qualifying candidate does not qualify when multiple consecutive windows required`() {
        val c1 = createCandidate(score = 0.75f, startMs = 1000L, endMs = 1200L)
        val result = engine.process(c1)
        assertNull("Single candidate cannot satisfy a 3-window requirement", result)
    }

    // ─── 4. Required number of qualifying candidates qualifies ────────────────

    @Test
    fun `required number of qualifying candidates qualifies`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.75f, startMs = 1300L, endMs = 1500L)
        val c3 = createCandidate(score = 0.80f, startMs = 1600L, endMs = 1800L)

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        val incident = engine.process(c3)

        assertNotNull("Three consecutive qualifying candidates should produce QualifiedIncident", incident)
        assertEquals(3, incident!!.qualifyingWindowCount)
        assertEquals(1000L, incident.windowStartMs)
        assertEquals(1800L, incident.windowEndMs)
    }

    // ─── 5. A non-qualifying candidate resets consecutive count ───────────────

    @Test
    fun `non-qualifying candidate resets consecutive count`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1300L, endMs = 1500L)
        val nonQualifying = createCandidate(score = 0.40f, startMs = 1600L, endMs = 1800L) // breaks streak
        val c3 = createCandidate(score = 0.70f, startMs = 1900L, endMs = 2100L)
        val c4 = createCandidate(score = 0.70f, startMs = 2200L, endMs = 2400L)

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        assertNull(engine.process(nonQualifying)) // resets count to 0
        assertNull(engine.process(c3))           // count = 1
        assertNull(engine.process(c4))           // count = 2 (still not 3)

        val c5 = createCandidate(score = 0.70f, startMs = 2500L, endMs = 2700L)
        val incident = engine.process(c5)        // count = 3
        assertNotNull("Should qualify after reaching 3 consecutive candidates after reset", incident)
    }

    // ─── 6. Qualifying candidates outside time window do not qualify ──────────

    @Test
    fun `qualifying candidates outside qualification time window do not qualify together`() {
        // qualificationWindowMillis = 2000L
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1300L, endMs = 1500L)
        // c3 is at 3500..3700; 3700 - 1000 = 2700 > 2000 ms
        val c3 = createCandidate(score = 0.70f, startMs = 3500L, endMs = 3700L)

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        val result = engine.process(c3)

        assertNull("Candidates spanning 2700ms exceed 2000ms window and must not qualify", result)
    }

    // ─── 7. Qualifying candidates inside time window qualify ──────────────────

    @Test
    fun `qualifying candidates inside time window qualify`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1300L, endMs = 1500L)
        val c3 = createCandidate(score = 0.70f, startMs = 1600L, endMs = 1800L)
        // 1800 - 1000 = 800ms <= 2000ms

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        val result = engine.process(c3)

        assertNotNull("Candidates within 800ms span must qualify", result)
    }

    // ─── 8. Exact minimum confidence score qualifies ──────────────────────────

    @Test
    fun `exact minimum confidence score qualifies`() {
        val singleConfig = EventQualificationConfig(
            minimumConfidenceScore = 0.60f,
            requiredConsecutiveWindows = 1,
            qualificationWindowMillis = 2000L
        )
        val singleEngine = EventQualificationEngine(singleConfig)

        val exactCandidate = createCandidate(score = 0.60f)
        val result = singleEngine.process(exactCandidate)

        assertNotNull("Candidate with score exactly equal to minimum must qualify", result)
    }

    // ─── 9. Score just below minimum does not qualify ─────────────────────────

    @Test
    fun `score just below minimum does not qualify`() {
        val singleConfig = EventQualificationConfig(
            minimumConfidenceScore = 0.60f,
            requiredConsecutiveWindows = 1,
            qualificationWindowMillis = 2000L
        )
        val singleEngine = EventQualificationEngine(singleConfig)

        val belowCandidate = createCandidate(score = 0.599f)
        val result = singleEngine.process(belowCandidate)

        assertNull("Score 0.599f below 0.60f must not qualify", result)
    }

    // ─── 10. Exact time-window boundary behaves consistently ──────────────────

    @Test
    fun `exact time-window boundary behaves consistently`() {
        // qualificationWindowMillis = 2000L
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1500L, endMs = 1700L)
        // 3000 - 1000 = 2000L (exactly boundary)
        val c3Boundary = createCandidate(score = 0.70f, startMs = 2800L, endMs = 3000L)

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        val result = engine.process(c3Boundary)

        assertNotNull("Sequence with duration exactly equal to qualificationWindowMillis must qualify", result)
    }

    @Test
    fun `duration one millisecond beyond qualification window does not qualify`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1500L, endMs = 1700L)
        // 3001 - 1000 = 2001L > 2000L
        val c3PastBoundary = createCandidate(score = 0.70f, startMs = 2800L, endMs = 3001L)

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        val result = engine.process(c3PastBoundary)

        assertNull("Sequence with duration 2001ms exceeding 2000ms must not qualify", result)
    }

    // ─── 11. Qualification produces correct start and end timestamps ──────────

    @Test
    fun `qualification produces correct start and end timestamps`() {
        val c1 = createCandidate(score = 0.70f, startMs = 5000L, endMs = 5200L)
        val c2 = createCandidate(score = 0.75f, startMs = 5300L, endMs = 5500L)
        val c3 = createCandidate(score = 0.80f, startMs = 5600L, endMs = 5800L)

        engine.process(c1)
        engine.process(c2)
        val incident = engine.process(c3)

        assertNotNull(incident)
        assertEquals(5000L, incident!!.windowStartMs)
        assertEquals(5800L, incident.windowEndMs)
    }

    // ─── 12. Qualification preserves relevant confidence information ──────────

    @Test
    fun `qualification preserves peak confidence score and candidate context`() {
        val c1 = createCandidate(score = 0.65f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.85f, startMs = 1300L, endMs = 1500L) // peak score
        val c3 = createCandidate(score = 0.70f, startMs = 1600L, endMs = 1800L)

        engine.process(c1)
        engine.process(c2)
        val incident = engine.process(c3)

        assertNotNull(incident)
        assertEquals(0.85f, incident!!.qualifyingConfidenceScore.value, 0.001f)
        assertEquals(3, incident.candidateEvents.size)
        assertEquals(c3, incident.triggeringCandidateEvent)
    }

    // ─── 13. Duplicate qualification is prevented ─────────────────────────────

    @Test
    fun `duplicate qualification is prevented for continuous ongoing sequence`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1300L, endMs = 1500L)
        val c3 = createCandidate(score = 0.70f, startMs = 1600L, endMs = 1800L)
        val c4 = createCandidate(score = 0.70f, startMs = 1900L, endMs = 2100L)
        val c5 = createCandidate(score = 0.70f, startMs = 2200L, endMs = 2400L)

        assertNull(engine.process(c1))
        assertNull(engine.process(c2))
        val firstIncident = engine.process(c3)
        assertNotNull("First qualification must be emitted", firstIncident)

        val duplicate1 = engine.process(c4)
        assertNull("Duplicate emission must be prevented for ongoing sequence", duplicate1)

        val duplicate2 = engine.process(c5)
        assertNull("Further duplicate emission must be prevented", duplicate2)
    }

    // ─── 14. Reset starts a fresh qualification sequence ──────────────────────

    @Test
    fun `reset clears state and allows subsequent qualification sequence`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        val c2 = createCandidate(score = 0.70f, startMs = 1300L, endMs = 1500L)
        val c3 = createCandidate(score = 0.70f, startMs = 1600L, endMs = 1800L)

        engine.process(c1)
        engine.process(c2)
        assertNotNull(engine.process(c3))

        // Reset
        engine.reset()

        // Fresh sequence
        val c4 = createCandidate(score = 0.70f, startMs = 5000L, endMs = 5200L)
        val c5 = createCandidate(score = 0.70f, startMs = 5300L, endMs = 5500L)
        val c6 = createCandidate(score = 0.70f, startMs = 5600L, endMs = 5800L)

        assertNull(engine.process(c4))
        assertNull(engine.process(c5))
        val secondIncident = engine.process(c6)
        assertNotNull("Second independent sequence must qualify after reset", secondIncident)
        assertEquals(5000L, secondIncident!!.windowStartMs)
    }

    // ─── 15. Extreme and invalid timestamps handled safely ────────────────────

    @Test
    fun `candidates with inverted timestamps are rejected and reset sequence`() {
        val c1 = createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L)
        engine.process(c1)

        // c2 has windowEndMs < windowStartMs (invalid duration)
        val inverted = createCandidate(score = 0.70f, startMs = 1500L, endMs = 1100L)
        val result = engine.process(inverted)

        assertNull("Inverted window timestamps must not qualify", result)
    }

    @Test
    fun `out-of-order candidate timestamps reset sequence`() {
        val c1 = createCandidate(score = 0.70f, startMs = 5000L, endMs = 5200L)
        val c2 = createCandidate(score = 0.70f, startMs = 5300L, endMs = 5500L)
        // c3 jumps back in time
        val outOfOrder = createCandidate(score = 0.70f, startMs = 2000L, endMs = 2200L)

        engine.process(c1)
        engine.process(c2)
        val result = engine.process(outOfOrder)

        assertNull("Out of order timestamp must reset sequence and not qualify", result)
    }

    // ─── CandidateEventWindow batch evaluation test ───────────────────────────

    @Test
    fun `evaluate on CandidateEventWindow produces correct QualifiedIncident`() {
        val window = CandidateEventWindow(
            listOf(
                createCandidate(score = 0.70f, startMs = 1000L, endMs = 1200L),
                createCandidate(score = 0.75f, startMs = 1300L, endMs = 1500L),
                createCandidate(score = 0.80f, startMs = 1600L, endMs = 1800L)
            )
        )
        val incident = engine.evaluate(window)
        assertNotNull(incident)
        assertEquals(3, incident!!.qualifyingWindowCount)
        assertEquals(1000L, incident.windowStartMs)
        assertEquals(1800L, incident.windowEndMs)
    }
}
