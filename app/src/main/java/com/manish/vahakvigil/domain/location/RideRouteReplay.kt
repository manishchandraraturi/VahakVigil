package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideLocationPoint
import com.manish.vahakvigil.domain.model.RideSessionPoint

/** Applies the same coordinate, accuracy, time-order and jump checks to persisted replay points. */
object RideRouteReplay {
    fun filterReliablePoints(points: List<RideSessionPoint>): List<RideSessionPoint> {
        val tracker = RidePathTracker(maxPoints = Int.MAX_VALUE)
        points.forEach { point ->
            tracker.addPoint(
                RideLocationPoint(point.latitude, point.longitude, point.accuracyMeters, point.timestampEpochMillis),
                currentTimeMs = point.timestampEpochMillis
            )
        }
        val pointsByTimestamp = points.associateBy { it.timestampEpochMillis }
        return tracker.getCurrentPath().mapNotNull { pointsByTimestamp[it.timestampMs] }
    }

    fun distanceMeters(points: List<RideSessionPoint>): Double? {
        val reliable = filterReliablePoints(points)
        if (reliable.size < 2) return null
        return reliable.zipWithNext().sumOf { (a, b) ->
            RidePathTracker.calculateDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
    }
}
