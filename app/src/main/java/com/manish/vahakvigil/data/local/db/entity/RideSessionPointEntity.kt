package com.manish.vahakvigil.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ride_session_points",
    foreignKeys = [ForeignKey(
        entity = RideSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId", "timestampEpochMillis"])]
)
data class RideSessionPointEntity(
    @PrimaryKey(autoGenerate = true) val pointId: Long = 0L,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val timestampEpochMillis: Long,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?
)
