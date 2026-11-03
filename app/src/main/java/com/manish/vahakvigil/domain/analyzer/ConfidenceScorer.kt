package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.ConfidenceScoringConfig
import com.manish.vahakvigil.domain.model.ConfidenceScoringInput
import com.manish.vahakvigil.domain.model.TemporalMotionEventType

/**
 * Pure Kotlin deterministic confidence scorer.
 *
 * Converts a [ConfidenceScoringInput] — aggregated sensor evidence from the current
 * temporal window — into a [ConfidenceScore] in the range [0.0, 1.0].
 *
 * ## Why confidence scoring exists
 *
 * Earlier pipeline stages ([MotionAnalyzer], [TemporalMotionAnalyzer]) detect whether
 * individual readings or short sequences of readings cross sensor thresholds. These binary
 * signals are necessary but not sufficient: a single threshold breach on a bumpy road looks
 * identical to an impact from a sensor-data perspective. Confidence scoring combines
 * MULTIPLE independent evidence sources into one continuous value, making the pipeline's
 * output easier to threshold and calibrate over time.
 *
 * ## Scoring formula
 *
 * ```
 * finalScore =
 *     patternWeight    × patternScore
 *   + accelerationWeight × accelerationScore
 *   + gyroscopeWeight    × gyroscopeScore
 *   + persistenceWeight  × persistenceScore
 * ```
 *
 * Each sub-score is independently in [0.0, 1.0]. The final score is clamped to [0.0, 1.0]
 * via [ConfidenceScore.fromRaw] to guard against floating-point drift.
 *
 * ### patternScore
 * - [TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE] → 1.0
 * - [TemporalMotionEventType.NO_SIGNIFICANT_EVENT]     → 0.0
 *
 * ### accelerationScore
 * - Linear: `min(peakAcc / referenceAccelerationMs2, 1.0)`
 * - 0.0 for zero acceleration; 1.0 when peak ≥ referenceAccelerationMs2.
 *
 * ### gyroscopeScore
 * - Linear: `min(peakGyro / referenceGyroscopeRads, 1.0)`
 * - 0.0 for zero rotation; 1.0 when peak ≥ referenceGyroscopeRads.
 *
 * ### persistenceScore
 * - Linear: `min(totalBreachCount / maxBreachesForFullPersistence, 1.0)`
 * - Rewards repeated evidence rather than isolated spikes.
 *
 * ## What this is NOT
 *
 * The resulting [ConfidenceScore] is NOT a probability of an accident. It is a
 * deterministic engineering score whose weights and reference values are
 * PLACEHOLDERS (see [ConfidenceScoringConfig]). Real-world labeled crash/non-crash
 * sensor recordings are required to calibrate these values before they can be used
 * reliably in production.
 *
 * No emergency action is triggered at this stage.
 *
 * ## Android independence
 *
 * This class has no dependency on Android framework types. It is fully
 * unit-testable on a plain JVM without instrumentation.
 *
 * @param config Scoring configuration. Defaults to [ConfidenceScoringConfig.DEFAULT].
 */
class ConfidenceScorer(
    private val config: ConfidenceScoringConfig = ConfidenceScoringConfig.DEFAULT
) {

    /**
     * Scores the provided [input] and returns a [ConfidenceScore] in [0.0, 1.0].
     *
     * The result is always finite and clamped — callers do not need to guard against
     * NaN or values outside [0.0, 1.0].
     *
     * @param input Aggregated sensor evidence for the current temporal window.
     * @return A [ConfidenceScore] reflecting the combined weight of all evidence factors.
     */
    fun score(input: ConfidenceScoringInput): ConfidenceScore {

        // ── Factor 1: Pattern (temporal impact sequence) ──────────────────────
        val patternScore = when (input.temporalEvent.eventType) {
            TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE -> 1.0f
            TemporalMotionEventType.NO_SIGNIFICANT_EVENT -> 0.0f
        }

        // ── Factor 2: Normalised peak acceleration ────────────────────────────
        // Linear mapping: 0 → 0.0, referenceAccelerationMs2 → 1.0, clamped.
        val accelerationScore = (input.peakAccelerationMagnitude / config.referenceAccelerationMs2)
            .coerceIn(0f, 1f)

        // ── Factor 3: Normalised peak gyroscope ───────────────────────────────
        // Linear mapping: 0 → 0.0, referenceGyroscopeRads → 1.0, clamped.
        val gyroscopeScore = (input.peakGyroscopeMagnitude / config.referenceGyroscopeRads)
            .coerceIn(0f, 1f)

        // ── Factor 4: Evidence density / persistence ──────────────────────────
        // Rewards repeated threshold breaches over isolated single spikes.
        val persistenceScore = (input.totalBreachCount.toFloat() /
                config.maxBreachesForFullPersistence.toFloat())
            .coerceIn(0f, 1f)

        // ── Weighted sum ──────────────────────────────────────────────────────
        val raw =
            config.patternWeight    * patternScore    +
            config.accelerationWeight * accelerationScore +
            config.gyroscopeWeight  * gyroscopeScore  +
            config.persistenceWeight * persistenceScore

        return ConfidenceScore.fromRaw(raw)
    }
}
