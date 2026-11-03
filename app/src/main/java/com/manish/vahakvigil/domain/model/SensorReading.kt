package com.manish.vahakvigil.domain.model

import kotlin.math.sqrt

/**
 * Domain model representing a synchronized sensor snapshot.
 *
 * Independent of Android framework classes ([android.hardware.SensorEvent])
 * to keep domain and business logic pure and unit-testable.
 *
 * @param timestamp Nanosecond or millisecond timestamp of the sensor event.
 * @param ax Linear acceleration along the X axis (m/s²).
 * @param ay Linear acceleration along the Y axis (m/s²).
 * @param az Linear acceleration along the Z axis (m/s²).
 * @param gx Angular velocity around the X axis (rad/s).
 * @param gy Angular velocity around the Y axis (rad/s).
 * @param gz Angular velocity around the Z axis (rad/s).
 * @param accelerationMagnitude Vector magnitude of linear acceleration: sqrt(ax² + ay² + az²).
 * @param gyroscopeMagnitude Vector magnitude of angular velocity: sqrt(gx² + gy² + gz²).
 */
data class SensorReading(
    val timestamp: Long = System.currentTimeMillis(),
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val accelerationMagnitude: Float = calculateMagnitude(ax, ay, az),
    val gyroscopeMagnitude: Float = calculateMagnitude(gx, gy, gz)
) {
    companion object {
        /**
         * Calculates 3D Euclidean vector magnitude: sqrt(x² + y² + z²).
         */
        fun calculateMagnitude(x: Float, y: Float, z: Float): Float {
            return sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        }
    }
}
