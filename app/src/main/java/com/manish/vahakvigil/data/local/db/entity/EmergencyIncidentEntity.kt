package com.manish.vahakvigil.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_incidents")
data class EmergencyIncidentEntity(
    @PrimaryKey val incidentId: String,
    val incidentTimestampEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val confidenceScore: Float,
    val evidenceSummary: String,
    val status: String,
    val userAction: String,
    val voiceNote: String? = null,
    val photoReference: String? = null
)
