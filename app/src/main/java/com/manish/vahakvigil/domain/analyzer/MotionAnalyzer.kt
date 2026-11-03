package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.MotionDetectionConfig
import com.manish.vahakvigil.domain.model.MotionEvent
import com.manish.vahakvigil.domain.model.MotionEventType
import com.manish.vahakvigil.domain.model.SensorReading

/**
 * Pure Kotlin motion analyzer that converts a [SensorReading] into a [MotionEvent]
 * when one or more sensor thresholds are exceeded.
 *
 * This class has NO dependencies on Android framework types (no [android.hardware.SensorEvent],
 * no [android.content.Context], no Compose, no ViewModel). It is fully unit-testable
 * on a plain JVM without any Android instrumentation.
 *
 * ### How it works
 *
 * For each [SensorReading], [analyze] checks whether the pre-calculated magnitudes
 * exceed the thresholds in [config]:
 *
 * - Both thresholds exceeded → [MotionEventType.COMBINED_IMPACT]
 * - Only acceleration threshold exceeded → [MotionEventType.HIGH_ACCELERATION]
 * - Only gyroscope threshold exceeded → [MotionEventType.HIGH_GYROSCOPE]
 * - Neither threshold exceeded → `null` (normal motion, no event produced)
 *
 * ### What this is NOT
 *
 * A non-null result does NOT mean a crash occurred. These are raw sensor signals.
 * A future confidence-scoring layer will be responsible for determining whether a
 * sequence of motion events constitutes a real accident.
 *
 * @param config Threshold configuration. Defaults to [MotionDetectionConfig] with
 *               placeholder values that must be calibrated before production use.
 */
class MotionAnalyzer(
    private val config: MotionDetectionConfig = MotionDetectionConfig()
) {

    /**
     * Analyzes a single [SensorReading] and returns a [MotionEvent] if any
     * configured threshold is exceeded, or `null` if the reading is within
     * normal operating bounds.
     *
     * @param reading The sensor snapshot to evaluate.
     * @return A [MotionEvent] describing the threshold breach, or `null` for normal motion.
     */
    fun analyze(reading: SensorReading): MotionEvent? {
        val highAcceleration = reading.accelerationMagnitude >= config.accelerationThresholdMs2
        val highGyroscope = reading.gyroscopeMagnitude >= config.gyroscopeThresholdRads

        val eventType = when {
            highAcceleration && highGyroscope -> MotionEventType.COMBINED_IMPACT
            highAcceleration -> MotionEventType.HIGH_ACCELERATION
            highGyroscope -> MotionEventType.HIGH_GYROSCOPE
            else -> return null
        }

        return MotionEvent(
            eventType = eventType,
            timestamp = reading.timestamp,
            accelerationMagnitude = reading.accelerationMagnitude,
            gyroscopeMagnitude = reading.gyroscopeMagnitude,
            ax = reading.ax,
            ay = reading.ay,
            az = reading.az,
            gx = reading.gx,
            gy = reading.gy,
            gz = reading.gz
        )
    }
}
