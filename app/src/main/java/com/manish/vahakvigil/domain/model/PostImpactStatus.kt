package com.manish.vahakvigil.domain.model

/**
 * Classification of post-impact motion observed after a [QualifiedIncident].
 *
 * ## Important distinction: SENSOR MOTION ONLY
 *
 * These states describe raw physical motion measured by the device sensors.
 * - [STILL] does NOT confirm a severe accident, incapacitation, or injury.
 * - [MOVING] does NOT confirm that the rider is safe or uninjured.
 * - [OBSERVING] means insufficient evidence has accumulated to reach a conclusion.
 */
enum class PostImpactStatus {
    /**
     * The post-impact observation window is currently in progress, and sustained
     * stillness or clear continuous motion has not yet been conclusively established.
     */
    OBSERVING,

    /**
     * Sensor readings in the post-impact window exhibit continuous or repeated motion
     * exceeding the stillness thresholds.
     */
    MOVING,

    /**
     * Sensor readings in the post-impact window have remained below the acceleration
     * and gyroscope stillness thresholds for at least the required sustained duration.
     */
    STILL
}
