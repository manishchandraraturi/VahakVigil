package com.manish.vahakvigil.domain.model

/**
 * Configuration model for [com.manish.vahakvigil.domain.analyzer.PostImpactMotionAnalyzer].
 *
 * Defines the parameters for observing post-incident motion and determining whether
 * sustained stillness is present after a [QualifiedIncident].
 *
 * ## ⚠️ PLACEHOLDER VALUES
 *
 * Every default value in this class — observation window, stillness thresholds, and
 * required sustained stillness duration — is an engineering placeholder chosen for
 * testing and development. None of these values have been validated against real-world
 * motorcycle accident dynamics.
 *
 * Real-world empirical crash and riding datasets are required to calibrate these values.
 *
 * @param postImpactWindowMillis
 *   Maximum duration (in milliseconds) after a [QualifiedIncident] during which sensor readings
 *   are collected and evaluated for post-impact motion analysis.
 *   Default: 5000L (5 seconds). PLACEHOLDER.
 *
 * @param stillnessAccelerationThreshold
 *   Maximum acceleration magnitude (m/s²) for a sensor reading to be considered still.
 *   On Earth, a stationary phone measures ~9.81 m/s² due to gravity. A threshold of 10.5 m/s²
 *   accommodates gravity plus minor sensor noise while remaining well below active riding motion.
 *   Default: 10.5 m/s². PLACEHOLDER.
 *
 * @param stillnessGyroscopeThreshold
 *   Maximum gyroscope magnitude (rad/s) for a sensor reading to be considered still.
 *   A stationary phone experiences near zero angular velocity. A threshold of 0.5 rad/s
 *   permits tiny sensor drift while detecting active rotation.
 *   Default: 0.5 rad/s. PLACEHOLDER.
 *
 * @param requiredStillnessDurationMillis
 *   Minimum continuous duration (in milliseconds) of unbroken still readings required to classify
 *   the post-impact state as [PostImpactStatus.STILL].
 *   Default: 2000L (2 seconds). PLACEHOLDER.
 */
data class PostImpactMotionConfig(
    val postImpactWindowMillis: Long = 5000L,
    val stillnessAccelerationThreshold: Float = 10.5f,
    val stillnessGyroscopeThreshold: Float = 0.5f,
    val requiredStillnessDurationMillis: Long = 2000L
) {
    init {
        require(postImpactWindowMillis > 0L) {
            "postImpactWindowMillis must be positive, got $postImpactWindowMillis"
        }
        require(stillnessAccelerationThreshold > 0f) {
            "stillnessAccelerationThreshold must be positive, got $stillnessAccelerationThreshold"
        }
        require(stillnessGyroscopeThreshold > 0f) {
            "stillnessGyroscopeThreshold must be positive, got $stillnessGyroscopeThreshold"
        }
        require(requiredStillnessDurationMillis > 0L) {
            "requiredStillnessDurationMillis must be positive, got $requiredStillnessDurationMillis"
        }
        require(requiredStillnessDurationMillis <= postImpactWindowMillis) {
            "requiredStillnessDurationMillis ($requiredStillnessDurationMillis) cannot exceed postImpactWindowMillis ($postImpactWindowMillis)"
        }
    }

    companion object {
        val DEFAULT: PostImpactMotionConfig = PostImpactMotionConfig()
    }
}
