package com.manish.vahakvigil.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_events",
    foreignKeys = [
        ForeignKey(
            entity = RideSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "timestampNanos"])]
)
data class SensorEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0L,
    val sessionId: String,
    val sensorType: String,
    val timestampNanos: Long,
    val timestampEpochMillis: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int
)
