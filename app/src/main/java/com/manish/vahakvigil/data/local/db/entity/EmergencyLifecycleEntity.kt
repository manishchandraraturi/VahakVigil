package com.manish.vahakvigil.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_lifecycle")
data class EmergencyLifecycleEntity(
    @PrimaryKey val incidentId: String,
    val incidentCreatedAtEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val confidenceScore: Float,
    val phase: String,
    val deadlineElapsedRealtimeMillis: Long,
    val deadlineEpochMillis: Long,
    val bootCount: Int,
    val userResponse: String,
    val dispatchState: String,
    val dispatchAttemptCount: Int,
    val dispatchAttemptedAtEpochMillis: Long?,
    val dispatchResolvedAtEpochMillis: Long?,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastLocationTimestampEpochMillis: Long?
)
