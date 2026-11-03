package com.manish.vahakvigil.domain.model

enum class RideSessionStatus { ACTIVE, COMPLETED, INTERRUPTED, INCOMPLETE }

data class RideSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val durationMillis: Long,
    val distanceMeters: Double?,
    val averageSpeedMetersPerSecond: Double?,
    val maximumSpeedMetersPerSecond: Double?,
    val status: RideSessionStatus
)

data class RideSessionPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampEpochMillis: Long,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?
)
