package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionEventType
import com.manish.vahakvigil.domain.model.MotionWindow
import com.manish.vahakvigil.domain.model.TemporalMotionEvent
import com.manish.vahakvigil.domain.model.TemporalMotionEventType

/**
 * Pure Kotlin analyzer that examines a [MotionWindow] — a short time slice of
 * recent [com.manish.vahakvigil.domain.model.SensorReading] values — and determines
 * whether the readings form a temporally significant motion pattern.
 *
 * ## Why temporal analysis exists
 *
 * [MotionAnalyzer] can identify whether an individual reading crosses a sensor
 * threshold, but a single spike is not sufficient evidence of a meaningful event.
 * Speed bumps, potholes, and sharp braking can produce isolated spikes. An impact
 * sequence tends to produce a SEQUENCE: a sudden high-acceleration event followed
 * closely by high rotation (gyroscope), reflecting the body/vehicle response.
 *
 * Temporal analysis distinguishes an isolated single-sensor spike from a
 * multi-sensor chronological pattern.
 *
 * ## What a temporal window means
 *
 * A temporal window is a sliding collection of the most recent sensor readings
 * bounded by [MotionDetectionConfig.temporalWindowMillis]. Readings older than
 * this duration relative to the newest reading are excluded from analysis.
 *
 * ## Pattern currently detected
 *
 * POSSIBLE_IMPACT_SEQUENCE is detected when, within the temporal window:
 *
 *   1. At least one reading has accelerationMagnitude ≥ accelerationThresholdMs2 (HIGH_ACCELERATION).
 *   2. At least one reading has gyroscopeMagnitude ≥ gyroscopeThresholdRads (HIGH_GYROSCOPE).
 *   3. The HIGH_ACCELERATION reading occurs BEFORE the HIGH_GYROSCOPE reading
 *      in chronological order (i.e. impact precedes rotation response).
 *
 * A window containing only one type of threshold breach, or where gyroscope
 * precedes acceleration, is classified as NO_SIGNIFICANT_EVENT.
 *
 * ## What this is NOT
 *
 * A POSSIBLE_IMPACT_SEQUENCE result is NOT a crash confirmation. Road surfaces,
 * speed bumps, emergency braking, and other riding events can produce the same
 * pattern. A future confidence-scoring milestone will use additional context
 * (pattern duration, magnitude levels, consecutive events) to refine this signal.
 *
 * ## Android independence
 *
 * This class has no dependency on Android framework types. It is fully
 * unit-testable on a plain JVM without instrumentation.
 *
 * @param config Threshold configuration shared with [MotionAnalyzer].
 *               Defaults to [MotionDetectionConfig] with placeholder values.
 * @param motionAnalyzer Per-reading classifier. Defaults to [MotionAnalyzer] using
 *                       the same config, to avoid duplicating threshold logic.
 */
class TemporalMotionAnalyzer(
    private val config: MotionDetectionConfig = MotionDetectionConfig(),
    private val motionAnalyzer: MotionAnalyzer = MotionAnalyzer(config)
) {

    /**
     * Analyzes a [MotionWindow] and returns a [TemporalMotionEvent] describing
     * the pattern found (or the absence of a significant pattern).
     *
     * Always returns a non-null result so callers have unambiguous access to
     * window metadata (timestamps, peak values, reading count) regardless of
     * the classification outcome.
     *
     * @param window The bounded time slice of recent sensor readings to examine.
     *               Readings must be in chronological order (oldest first).
     * @return [TemporalMotionEvent] with [TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE]
     *         if the pattern is detected, [TemporalMotionEventType.NO_SIGNIFICANT_EVENT] otherwise.
     */
    fun analyze(window: MotionWindow): TemporalMotionEvent {
        val readings = window.readings

        // Window metadata — use safe defaults when empty
        val windowStart = window.startTimestamp ?: 0L
        val windowEnd = window.endTimestamp ?: 0L
        val readingCount = readings.size

        // Accumulate peak values for future confidence scoring
        var peakAcc = 0f
        var peakGyro = 0f
        for (r in readings) {
            if (r.accelerationMagnitude > peakAcc) peakAcc = r.accelerationMagnitude
            if (r.gyroscopeMagnitude > peakGyro) peakGyro = r.gyroscopeMagnitude
        }

        // Empty or single-reading windows cannot form a multi-sensor sequence
        if (readings.size < 2) {
            return TemporalMotionEvent(
                eventType = TemporalMotionEventType.NO_SIGNIFICANT_EVENT,
                windowStartMs = windowStart,
                windowEndMs = windowEnd,
                peakAccelerationMagnitude = peakAcc,
                peakGyroscopeMagnitude = peakGyro,
                readingCount = readingCount
            )
        }

        // Chronological scan:
        // Search for the earliest HIGH_ACCELERATION reading, then look for a
        // HIGH_GYROSCOPE reading that occurs AFTER it in the same window.
        // This enforces the ordering: acceleration impact → rotation response.
        var firstHighAccIndex = -1
        for (i in readings.indices) {
            val event = motionAnalyzer.analyze(readings[i])
            if (event != null &&
                (event.eventType == MotionEventType.HIGH_ACCELERATION ||
                        event.eventType == MotionEventType.COMBINED_IMPACT)
            ) {
                firstHighAccIndex = i
                break
            }
        }

        val isPossibleSequence = if (firstHighAccIndex >= 0) {
            // Check whether any subsequent reading has high gyroscope
            var foundFollowingGyro = false
            for (i in (firstHighAccIndex + 1)..readings.lastIndex) {
                val event = motionAnalyzer.analyze(readings[i])
                if (event != null &&
                    (event.eventType == MotionEventType.HIGH_GYROSCOPE ||
                            event.eventType == MotionEventType.COMBINED_IMPACT)
                ) {
                    foundFollowingGyro = true
                    break
                }
            }
            foundFollowingGyro
        } else {
            false
        }

        return TemporalMotionEvent(
            eventType = if (isPossibleSequence) {
                TemporalMotionEventType.POSSIBLE_IMPACT_SEQUENCE
            } else {
                TemporalMotionEventType.NO_SIGNIFICANT_EVENT
            },
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            peakAccelerationMagnitude = peakAcc,
            peakGyroscopeMagnitude = peakGyro,
            readingCount = readingCount
        )
    }
}
