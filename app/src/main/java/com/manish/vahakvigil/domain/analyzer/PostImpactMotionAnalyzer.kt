package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.PostImpactMotionConfig
import com.manish.vahakvigil.domain.model.PostImpactMotionResult
import com.manish.vahakvigil.domain.model.PostImpactMotionWindow
import com.manish.vahakvigil.domain.model.PostImpactStatus
import com.manish.vahakvigil.domain.model.QualifiedIncident
import com.manish.vahakvigil.domain.model.SensorReading

/**
 * Pure Kotlin analyzer that observes post-impact sensor readings following a [QualifiedIncident]
 * and evaluates whether sustained stillness or ongoing motion is present.
 *
 * ## Responsibilities
 * - Ignores all readings recorded prior to [QualifiedIncident.windowEndMs].
 * - Binds analysis strictly within [PostImpactMotionConfig.postImpactWindowMillis] after the incident.
 * - Enforces chronological reading order safely without artificial duration inflation.
 * - Requires BOTH acceleration magnitude <= [PostImpactMotionConfig.stillnessAccelerationThreshold]
 *   AND gyroscope magnitude <= [PostImpactMotionConfig.stillnessGyroscopeThreshold] for stillness.
 * - Requires continuous unbroken stillness for at least [PostImpactMotionConfig.requiredStillnessDurationMillis]
 *   before classifying state as [PostImpactStatus.STILL].
 * - Classifies active motion as [PostImpactStatus.MOVING] and in-progress observation as [PostImpactStatus.OBSERVING].
 *
 * ## Android independence
 * Pure JVM Kotlin. Completely decoupled from SensorManager, SensorEvent, Context, and UI layers.
 *
 * @param config Post-impact configuration rules. Defaults to [PostImpactMotionConfig.DEFAULT].
 */
class PostImpactMotionAnalyzer(
    val config: PostImpactMotionConfig = PostImpactMotionConfig.DEFAULT
) {

    /**
     * Analyzes post-incident sensor readings in the context of a [QualifiedIncident].
     *
     * @param incident The qualified sensor incident being analyzed, or null if none has qualified.
     * @param window The window of post-impact sensor readings.
     * @return [PostImpactMotionResult] describing the observed motion state.
     */
    fun analyze(
        incident: QualifiedIncident?,
        window: PostImpactMotionWindow
    ): PostImpactMotionResult {
        if (incident == null) {
            return PostImpactMotionResult(
                status = PostImpactStatus.OBSERVING,
                observationStartMs = 0L,
                observationEndMs = 0L,
                stillnessDurationMs = 0L,
                peakAccelerationMagnitude = 0f,
                peakGyroscopeMagnitude = 0f,
                readingCount = 0
            )
        }

        val incidentEnd = incident.windowEndMs
        val windowLimit = incidentEnd + config.postImpactWindowMillis

        // Reject/ignore readings before the incident timestamp or beyond the observation window
        val validReadings = window.readings.filter {
            it.timestamp > incidentEnd && it.timestamp <= windowLimit
        }

        // Safely enforce chronological ordering: drop out-of-order readings
        val chronologicalReadings = mutableListOf<SensorReading>()
        var lastTs = incidentEnd
        for (r in validReadings) {
            if (r.timestamp >= lastTs) {
                chronologicalReadings.add(r)
                lastTs = r.timestamp
            }
        }

        if (chronologicalReadings.isEmpty()) {
            return PostImpactMotionResult(
                status = PostImpactStatus.OBSERVING,
                observationStartMs = incidentEnd,
                observationEndMs = incidentEnd,
                stillnessDurationMs = 0L,
                peakAccelerationMagnitude = 0f,
                peakGyroscopeMagnitude = 0f,
                readingCount = 0
            )
        }

        val observationStart = chronologicalReadings.first().timestamp
        val observationEnd = chronologicalReadings.last().timestamp

        var currentStreakStart: Long? = null
        var currentStillnessDuration = 0L
        var peakAcc = 0f
        var peakGyro = 0f
        var hasMovingReading = false

        for (r in chronologicalReadings) {
            val rawAcc = r.accelerationMagnitude
            val rawGyro = r.gyroscopeMagnitude

            val isAccValid = !rawAcc.isNaN() && !rawAcc.isInfinite()
            val isGyroValid = !rawGyro.isNaN() && !rawGyro.isInfinite()

            if (isAccValid && rawAcc > peakAcc) peakAcc = rawAcc
            if (isGyroValid && rawGyro > peakGyro) peakGyro = rawGyro

            val isStill = isAccValid && isGyroValid &&
                    rawAcc <= config.stillnessAccelerationThreshold &&
                    rawGyro <= config.stillnessGyroscopeThreshold

            if (isStill) {
                if (currentStreakStart == null) {
                    currentStreakStart = r.timestamp
                }
                currentStillnessDuration = r.timestamp - currentStreakStart
            } else {
                hasMovingReading = true
                currentStreakStart = null
                currentStillnessDuration = 0L
            }
        }

        val status = when {
            currentStillnessDuration >= config.requiredStillnessDurationMillis -> PostImpactStatus.STILL
            currentStreakStart != null -> PostImpactStatus.OBSERVING
            hasMovingReading -> PostImpactStatus.MOVING
            else -> PostImpactStatus.OBSERVING
        }

        return PostImpactMotionResult(
            status = status,
            observationStartMs = observationStart,
            observationEndMs = observationEnd,
            stillnessDurationMs = currentStillnessDuration,
            peakAccelerationMagnitude = peakAcc,
            peakGyroscopeMagnitude = peakGyro,
            readingCount = chronologicalReadings.size
        )
    }
}
