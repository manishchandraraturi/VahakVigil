package com.manish.vahakvigil.domain.model

/**
 * Classifies the result of a temporal pattern analysis performed by
 * [com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer] over a [MotionWindow].
 *
 * These classifications describe motion PATTERNS across multiple readings,
 * not single-reading threshold breaches.
 *
 * ⚠️ None of these values indicate that a crash has occurred.
 * Crash determination requires further confidence scoring and real-world
 * validation data that is not yet implemented.
 */
enum class TemporalMotionEventType {

    /**
     * The [MotionWindow] did not contain a recognizable multi-sensor temporal pattern.
     *
     * This covers: empty windows, windows with only isolated spikes, and windows
     * containing no threshold-exceeding readings at all.
     */
    NO_SIGNIFICANT_EVENT,

    /**
     * The [MotionWindow] contains a temporally ordered multi-sensor pattern that
     * may be consistent with a physical impact: high acceleration followed by
     * high gyroscope rotation within the configured [MotionDetectionConfig.temporalWindowMillis].
     *
     * This is a raw motion signal. It is NOT a confirmed crash.
     * Multiple real-world scenarios (bumpy road, speed bump, sharp turn) can
     * also produce this pattern. A future confidence-scoring layer will
     * differentiate between them using additional context.
     */
    POSSIBLE_IMPACT_SEQUENCE
}
