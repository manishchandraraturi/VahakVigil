package com.manish.vahakvigil.domain.model

data class EmergencyIncidentRecord(
    val incidentId: String,
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
) {
    init {
        require(incidentId.isNotBlank())
        require(confidenceScore.isFinite() && confidenceScore in 0f..1f)
        require((latitude == null) == (longitude == null))
        if (latitude != null && longitude != null) require(EmergencyLocation.isValidCoordinate(latitude, longitude))
    }
}
