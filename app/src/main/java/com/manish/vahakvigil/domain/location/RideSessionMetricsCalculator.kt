package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideSessionPoint
import kotlin.math.abs

data class RideSessionMetrics(
    val distanceMeters: Double?,
    val averageSpeedMetersPerSecond: Double?,
    val maximumSpeedMetersPerSecond: Double?
)

/** Calculates trip statistics from the same reliable GPS path used by route replay. */
object RideSessionMetricsCalculator {
    const val MAX_METRIC_ACCURACY_METERS = 20f
    const val MIN_SPEED_INTERVAL_MILLIS = 1_000L
    const val MAX_SPEED_INTERVAL_MILLIS = 20_000L

    fun calculate(points: List<RideSessionPoint>, durationMillis: Long): RideSessionMetrics {
        val accurateInput = points.filter { point ->
            point.accuracyMeters?.let { it.isFinite() && it >= 0f && it <= MAX_METRIC_ACCURACY_METERS } == true
        }
        val reliable = RideRouteReplay.filterReliablePoints(accurateInput)
        if (reliable.size < 2) return RideSessionMetrics(null, null, null)

        val segments = reliable.zipWithNext().mapNotNull { (a, b) ->
            val elapsed = b.timestampEpochMillis - a.timestampEpochMillis
            if (elapsed <= 0L) return@mapNotNull null
            val meters = RidePathTracker.calculateDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val routeSpeed = meters / (elapsed / 1000.0)
            if (!routeSpeed.isFinite() || routeSpeed > RidePathTracker.MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND) return@mapNotNull null

            val reportedSpeed = b.speedMetersPerSecond?.toDouble()
            val speed = when {
                reportedSpeed == null -> routeSpeed
                !reportedSpeed.isFinite() || reportedSpeed < 0.0 || reportedSpeed > RidePathTracker.MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND -> null
                abs(reportedSpeed - routeSpeed) > maxOf(5.0, routeSpeed * 0.6) -> null
                else -> reportedSpeed
            }
            Segment(meters, speed, elapsed)
        }
        if (segments.isEmpty()) return RideSessionMetrics(null, null, null)

        val distance = segments.sumOf(Segment::distanceMeters)
        val average = durationMillis.takeIf { it > 0L && distance.isFinite() }?.let { distance / (it / 1000.0) }
        val confirmableSpeeds = segments.indices.flatMap { index ->
            val current = segments[index]
            val next = segments.getOrNull(index + 1) ?: return@flatMap emptyList()
            val firstSpeed = current.speedMetersPerSecond ?: return@flatMap emptyList()
            val secondSpeed = next.speedMetersPerSecond ?: return@flatMap emptyList()
            if (current.intervalMillis !in MIN_SPEED_INTERVAL_MILLIS..MAX_SPEED_INTERVAL_MILLIS ||
                next.intervalMillis !in MIN_SPEED_INTERVAL_MILLIS..MAX_SPEED_INTERVAL_MILLIS
            ) return@flatMap emptyList()
            if (abs(firstSpeed - secondSpeed) > maxOf(5.0, maxOf(firstSpeed, secondSpeed) * 0.5)) return@flatMap emptyList()
            listOf(firstSpeed, secondSpeed)
        }
        return RideSessionMetrics(distance, average, confirmableSpeeds.maxOrNull())
    }

    private data class Segment(
        val distanceMeters: Double,
        val speedMetersPerSecond: Double?,
        val intervalMillis: Long
    )
}
