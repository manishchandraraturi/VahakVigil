package com.manish.vahakvigil.domain.model

/**
 * Configuration object that holds the threshold values used by
 * [com.manish.vahakvigil.domain.analyzer.MotionAnalyzer] and
 * [com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer].
 *
 * ⚠️ WARNING — PLACEHOLDER VALUES:
 * The default thresholds and window duration below have NOT been validated
 * against real motorcycle accident data. They are rough engineering estimates
 * chosen to allow early development and testing without triggering on normal riding.
 *
 * A calibration study using real-world crash data is required before
 * these values can be considered reliable for production use.
 * Do NOT present these defaults to users as scientifically accurate thresholds.
 *
 * @param accelerationThresholdMs2
 *   Minimum acceleration magnitude (m/s²) required to trigger a
 *   [MotionEventType.HIGH_ACCELERATION] or [MotionEventType.COMBINED_IMPACT] event.
 *   Default: 20 m/s² — roughly 2× gravity. PLACEHOLDER — not validated.
 *
 * @param gyroscopeThresholdRads
 *   Minimum gyroscope magnitude (rad/s) required to trigger a
 *   [MotionEventType.HIGH_GYROSCOPE] or [MotionEventType.COMBINED_IMPACT] event.
 *   Default: 3 rad/s — approximately half a rotation per second. PLACEHOLDER — not validated.
 *
 * @param temporalWindowMillis
 *   Duration (in milliseconds) of the sliding time window used by
 *   [com.manish.vahakvigil.domain.analyzer.TemporalMotionAnalyzer].
 *   Only readings whose timestamps fall within this window of the most recent reading
 *   are considered when scanning for multi-sensor temporal patterns.
 *   Default: 500 ms. PLACEHOLDER — not scientifically validated. Shorter windows reduce
 *   false positives; longer windows may capture slower motion patterns. Real-world
 *   accident data is required to choose a defensible value.
 */
data class MotionDetectionConfig(
    val accelerationThresholdMs2: Float = 20f,
    val gyroscopeThresholdRads: Float = 3f,
    val temporalWindowMillis: Long = 500L
)
