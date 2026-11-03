package com.manish.vahakvigil.domain.model

/**
 * Domain model representing a single detected motion event.
 *
 * A [MotionEvent] is produced by [com.manish.vahakvigil.domain.analyzer.MotionAnalyzer]
 * when one or more sensor readings exceed a configured threshold.
 *
 * This is a raw signal — it is NOT a confirmed crash event. Crash
 * determination requires additional confidence scoring which will be
 * implemented in a later milestone.
 *
 * All raw sensor values from the originating [SensorReading] are preserved
 * here so that future confidence-scoring algorithms have full context without
 * needing to re-read sensor history.
 *
 * @param eventType      Classification of which threshold(s) were exceeded.
 * @param timestamp      Timestamp of the originating [SensorReading] (ms or ns).
 * @param accelerationMagnitude Overall 3D acceleration magnitude (m/s²).
 * @param gyroscopeMagnitude    Overall 3D gyroscope magnitude (rad/s).
 * @param ax Acceleration along the X axis (m/s²).
 * @param ay Acceleration along the Y axis (m/s²).
 * @param az Acceleration along the Z axis (m/s²).
 * @param gx Angular velocity around the X axis (rad/s).
 * @param gy Angular velocity around the Y axis (rad/s).
 * @param gz Angular velocity around the Z axis (rad/s).
 */
data class MotionEvent(
    val eventType: MotionEventType,
    val timestamp: Long,
    val accelerationMagnitude: Float,
    val gyroscopeMagnitude: Float,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float
)
