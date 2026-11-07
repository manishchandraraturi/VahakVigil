package com.manish.vahakvigil.domain.model

/** One immutable hardware sensor event; accelerometer and gyroscope samples are never combined. */
data class RawSensorEvent(
    val type: RawSensorType,
    val timestampNanos: Long,
    val timestampEpochMillis: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int
)

enum class RawSensorType {
    ACCELEROMETER,
    GYROSCOPE
}
