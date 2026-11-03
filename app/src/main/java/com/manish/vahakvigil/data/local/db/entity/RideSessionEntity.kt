package com.manish.vahakvigil.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ride_sessions")
data class RideSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val durationMillis: Long = 0L,
    val distanceMeters: Double? = null,
    val averageSpeedMetersPerSecond: Double? = null,
    val maximumSpeedMetersPerSecond: Double? = null,
    val status: String = "ACTIVE"
)
