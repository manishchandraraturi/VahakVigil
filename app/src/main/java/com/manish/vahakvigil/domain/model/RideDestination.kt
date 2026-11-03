package com.manish.vahakvigil.domain.model

data class RideDestination(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(EmergencyLocation.isValidCoordinate(latitude, longitude))
    }
}

data class RideRoutePlan(
    val points: List<GeoPoint>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Long,
    val remainingDistanceMeters: Double? = null,
    val estimatedArrivalEpochMillis: Long? = null
) {
    init {
        require(points.size >= 2)
        require(totalDistanceMeters > 0.0)
        require(estimatedDurationSeconds > 0L)
        require(remainingDistanceMeters == null || remainingDistanceMeters >= 0.0)
    }
}

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(EmergencyLocation.isValidCoordinate(latitude, longitude)) }
}

data class SafetyOverlayFeature(
    val id: String,
    val points: List<GeoPoint>,
    val source: String,
    val observedAtEpochMillis: Long
)
