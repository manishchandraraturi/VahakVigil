package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.ConfidenceScoringConfig
import com.manish.vahakvigil.domain.model.ConfidenceScoringInput
import com.manish.vahakvigil.domain.model.EvidenceLevel
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.TemporalMotionEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [ConfidenceScorer] and associated domain models.
 *
 * All tests execute purely on the JVM with deterministic inputs.
 * No Android APIs, random numbers, or real clocks are used.
 */
class ConfidenceScorerTest {

    private val defaultConfig = ConfidenceScoringConfig.DEFAULT
    private val scorer = ConfidenceScorer(defaultConfig)

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun createTemporalEvent(
        eventType: TemporalMotionEventType = TemporalMotionEventType.NO_SIGNIFICANT_EVENT,
        startMs: Long = 1000L,
        endMs: Long = 1400L,
        peakAcc: Float = 1.0f,
        peakGyro: Float = 0.1f,
        readingCount: Int = 5
    ) = TemporalMotionEvent(
        eventType = eventType,
        windowStartMs = startMs,
        windowEndMs = endMs,
        peakAccelerationMagnitude = peakAcc,
        peakGyroscopeMagnitude = peakGyro,
        readingCount = readingCount
    )

    private fun createInput(
        eventType: TemporalMotionEventType = TemporalMotionEventType.NO_SIGNIFICANT_EVENT,
        peakAcc: Float = 1.0f,
        peakGyro: Float = 0.1f,
        accBreaches: Int = 0,
        gyroBreaches: Int = 0,
        readingCount: Int = 5,
        startMs: Long = 1000L,
        endMs: Long = 1400L
    ): ConfidenceScoringInput {
        val temporalEvent = createTemporalEvent(
            eventType = eventType,
            startMs = startMs,
            endMs = endMs,
            peakAcc = peakAcc,
            peakGyro = peakGyro,
            readingCount = readingCount
        )
        return ConfidenceScoringInput(
            temporalEvent = temporalEvent,
            peakAccelerationMagnitude = peakAcc,
            peakGyroscopeMagnitude = peakGyro,
            accelerationBreachCount = accBreaches,
            gyroscopeBreachCount = gyroBreaches,
            windowReadingCount = readingCount
        )
    }

    // ─── 1. Normal input produces a low score ─────────────────────────────────

    @Test
    fun `normal input produces a low score`() {
        val input = createInput(
            eventType = TemporalMotionEventType.NO_SIGNIFICANT_EVENT,
            peakAcc = 2.0f,
            peakGyro = 0.2f,
            accBreaches = 0,
            gyroBreaches = 0
        )
        val score = scorer.score(input)

        assertTrue(
            "Normal input should yield low score, was ${score.value}",
            score.value < 0.15f
        )
        assertEquals(EvidenceLevel.LOW, score.evidenceLevel)
    }

    // ─── 2. Possible impact sequence increases the score ──────────────────────

    @Test
    fun `possible impact sequence increases score by pattern weight`() {
        val baseInput = createInput(
            eventType = TemporalMotionEventType.NO_SIGNIFICANT_EVENT,
            peakAcc = 20.0f,
            peakGyro = 3.0f,
            accBreaches = 1,
            gyroBreaches = 1
        )
        val sequenceInput = createInput(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            peakAcc = 20.0f,
            peakGyro = 3.0f,
            accBreaches = 1,
            gyroBreaches = 1
        )

        val baseScore = scorer.score(baseInput)
        val sequenceScore = scorer.score(sequenceInput)

        assertTrue(
            "Sequence score should be strictly greater than base score",
            sequenceScore.value > baseScore.value
        )
        assertEquals(
            defaultConfig.patternWeight,
            sequenceScore.value - baseScore.value,
            0.001f
        )
    }

    // ─── 3. Higher acceleration increases acceleration contribution ───────────

    @Test
    fun `higher acceleration increases acceleration contribution`() {
        val lowAccInput = createInput(peakAcc = 10.0f)
        val highAccInput = createInput(peakAcc = 40.0f)

        val lowScore = scorer.score(lowAccInput)
        val highScore = scorer.score(highAccInput)

        assertTrue(
            "Higher peak acceleration must increase the final score",
            highScore.value > lowScore.value
        )
    }

    // ─── 4. Higher gyroscope increases gyroscope contribution ─────────────────

    @Test
    fun `higher gyroscope increases gyroscope contribution`() {
        val lowGyroInput = createInput(peakGyro = 1.0f)
        val highGyroInput = createInput(peakGyro = 8.0f)

        val lowScore = scorer.score(lowGyroInput)
        val highScore = scorer.score(highGyroInput)

        assertTrue(
            "Higher peak gyroscope must increase the final score",
            highScore.value > lowScore.value
        )
    }

    // ─── 5. Repeated evidence increases persistence contribution ──────────────

    @Test
    fun `repeated threshold breaches increase persistence contribution`() {
        val singleBreachInput = createInput(accBreaches = 1, gyroBreaches = 0)
        val multipleBreachesInput = createInput(accBreaches = 3, gyroBreaches = 2)

        val singleScore = scorer.score(singleBreachInput)
        val multipleScore = scorer.score(multipleBreachesInput)

        assertTrue(
            "Multiple breaches must increase the persistence score",
            multipleScore.value > singleScore.value
        )
    }

    // ─── 6. No temporal event does not receive impact-pattern contribution ────

    @Test
    fun `NO_SIGNIFICANT_EVENT contributes zero to pattern factor`() {
        val noEventInput = createInput(
            eventType = TemporalMotionEventType.NO_SIGNIFICANT_EVENT,
            peakAcc = 0f,
            peakGyro = 0f,
            accBreaches = 0,
            gyroBreaches = 0
        )
        val score = scorer.score(noEventInput)

        assertEquals(0.0f, score.value, 0.0001f)
    }

    // ─── 7. Final score is never below 0_0 ────────────────────────────────────

    @Test
    fun `final score is never below 0_0`() {
        val zeroInput = createInput(
            peakAcc = 0f,
            peakGyro = 0f,
            accBreaches = 0,
            gyroBreaches = 0
        )
        val score = scorer.score(zeroInput)

        assertTrue("Score must be >= 0.0", score.value >= 0.0f)
    }

    // ─── 8. Final score is never above 1_0 ────────────────────────────────────

    @Test
    fun `final score is never above 1_0 even with extreme input`() {
        val extremeInput = createInput(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            peakAcc = 500.0f,    // 10x reference
            peakGyro = 100.0f,   // 10x reference
            accBreaches = 50,
            gyroBreaches = 50
        )
        val score = scorer.score(extremeInput)

        assertTrue("Score must be <= 1.0", score.value <= 1.0f)
        assertEquals(1.0f, score.value, 0.0001f)
        assertEquals(EvidenceLevel.HIGH, score.evidenceLevel)
    }

    // ─── 9. Extreme values do not produce NaN ─────────────────────────────────

    @Test
    fun `extreme and special values do not produce NaN`() {
        val nanInput = createInput(
            peakAcc = Float.NaN,
            peakGyro = 0f
        )
        val score = scorer.score(nanInput)

        assertTrue("Score must not be NaN", !score.value.isNaN())
        assertTrue("Score must be valid range", score.value in 0.0f..1.0f)
    }

    // ─── 10. Extreme values do not produce Infinity ───────────────────────────

    @Test
    fun `infinite values do not produce Infinity`() {
        val infInput = createInput(
            peakAcc = Float.POSITIVE_INFINITY,
            peakGyro = Float.POSITIVE_INFINITY
        )
        val score = scorer.score(infInput)

        assertTrue("Score must not be infinite", !score.value.isInfinite())
        assertTrue("Score must be valid range", score.value in 0.0f..1.0f)
    }

    // ─── 11. Configurable weights affect final score ──────────────────────────

    @Test
    fun `custom weights correctly scale score components`() {
        val customConfig = ConfidenceScoringConfig.create(
            patternWeight = 0.70f,
            accelerationWeight = 0.10f,
            gyroscopeWeight = 0.10f,
            persistenceWeight = 0.10f,
            referenceAccelerationMs2 = 50f,
            referenceGyroscopeRads = 10f,
            maxBreachesForFullPersistence = 5
        )
        val customScorer = ConfidenceScorer(customConfig)

        val input = createInput(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            peakAcc = 0f,
            peakGyro = 0f,
            accBreaches = 0,
            gyroBreaches = 0
        )
        val score = customScorer.score(input)

        assertEquals(0.70f, score.value, 0.001f)
    }

    // ─── 12. Invalid weights and configurations are rejected ──────────────────

    @Test
    fun `invalid weights not summing to 1_0 throw IllegalArgumentException`() {
        try {
            ConfidenceScoringConfig.create(
                patternWeight = 0.50f,
                accelerationWeight = 0.50f,
                gyroscopeWeight = 0.50f,
                persistenceWeight = 0.10f
            )
            fail("Expected IllegalArgumentException when weights do not sum to 1.0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must sum to 1.0") == true)
        }
    }

    @Test
    fun `non-positive reference values throw IllegalArgumentException`() {
        try {
            ConfidenceScoringConfig.create(referenceAccelerationMs2 = 0f)
            fail("Expected IllegalArgumentException for non-positive reference acceleration")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("referenceAccelerationMs2") == true)
        }

        try {
            ConfidenceScoringConfig.create(referenceGyroscopeRads = -1f)
            fail("Expected IllegalArgumentException for negative reference gyroscope")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("referenceGyroscopeRads") == true)
        }

        try {
            ConfidenceScoringConfig.create(maxBreachesForFullPersistence = 0)
            fail("Expected IllegalArgumentException for zero maxBreachesForFullPersistence")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("maxBreachesForFullPersistence") == true)
        }
    }

    // ─── 13. CandidateAccidentEvent contains expected score and timestamps ────

    @Test
    fun `CandidateAccidentEvent correctly stores timestamps, score, and context`() {
        val temporalEvent = createTemporalEvent(
            eventType = TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE,
            startMs = 1500L,
            endMs = 1950L,
            peakAcc = 35.0f,
            peakGyro = 5.0f,
            readingCount = 8
        )
        val score = ConfidenceScore(0.75f)

        val candidate = CandidateAccidentEvent(
            windowStartMs = temporalEvent.windowStartMs,
            windowEndMs = temporalEvent.windowEndMs,
            confidenceScore = score,
            temporalEvent = temporalEvent,
            readingCount = temporalEvent.readingCount
        )

        assertEquals(1500L, candidate.windowStartMs)
        assertEquals(1950L, candidate.windowEndMs)
        assertEquals(0.75f, candidate.confidenceScore.value, 0.001f)
        assertEquals(EvidenceLevel.HIGH, candidate.confidenceScore.evidenceLevel)
        assertEquals(temporalEvent, candidate.temporalEvent)
        assertEquals(8, candidate.readingCount)
    }

    // ─── ConfidenceScore domain model tests ───────────────────────────────────

    @Test
    fun `ConfidenceScore clamp validation and EvidenceLevels`() {
        val lowScore = ConfidenceScore.fromRaw(-0.5f)
        assertEquals(0.0f, lowScore.value, 0.0f)
        assertEquals(EvidenceLevel.LOW, lowScore.evidenceLevel)

        val medScore = ConfidenceScore.fromRaw(0.50f)
        assertEquals(0.50f, medScore.value, 0.0f)
        assertEquals(EvidenceLevel.MEDIUM, medScore.evidenceLevel)

        val highScore = ConfidenceScore.fromRaw(1.5f)
        assertEquals(1.0f, highScore.value, 0.0f)
        assertEquals(EvidenceLevel.HIGH, highScore.evidenceLevel)
    }
}
