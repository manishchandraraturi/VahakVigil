package com.manish.vahakvigil.domain.model

/**
 * Classifies the type of motion event detected during a sensor reading.
 *
 * These are raw motion classifications, NOT confirmed crash events.
 * A [MotionEventType] only indicates that one or more sensor thresholds
 * were exceeded. Further analysis is required to determine whether a
 * real-world accident occurred.
 */
enum class MotionEventType {

    /**
     * The calculated acceleration magnitude exceeded the configured threshold.
     *
     * Cause: accelerometer reading above [MotionDetectionConfig.accelerationThresholdMs2].
     * Does NOT imply a crash.
     */
    HIGH_ACCELERATION,

    /**
     * The calculated gyroscope magnitude exceeded the configured threshold.
     *
     * Cause: gyroscope reading above [MotionDetectionConfig.gyroscopeThresholdRads].
     * Does NOT imply a crash.
     */
    HIGH_GYROSCOPE,

    /**
     * Both acceleration and gyroscope magnitudes exceeded their respective thresholds
     * in the same reading.
     *
     * This is a stronger signal than either alone, but still NOT a confirmed crash.
     * It requires further context and confidence scoring in a later milestone.
     */
    COMBINED_IMPACT
}
