package com.manish.vahakvigil.domain.hospital

import com.manish.vahakvigil.domain.model.EmergencyCapabilityStatus
import com.manish.vahakvigil.domain.model.EmergencyHospital
import com.manish.vahakvigil.domain.model.EmergencyLocation

/**
 * Result of ranking and selecting the best emergency hospital candidate.
 *
 * @param hospital Selected candidate, or null if no candidates were found.
 * @param reasoning Deterministic, auditable explanation of the selection decision.
 */
data class EmergencyHospitalSelection(
    val hospital: EmergencyHospital?,
    val reasoning: String
)

/**
 * Deterministic ranker for emergency hospital candidates.
 *
 * Ranks candidates using explicit documented factors in order of priority:
 * 1. Verified emergency/trauma capability ([EmergencyCapabilityStatus.VERIFIED] > [EmergencyCapabilityStatus.UNVERIFIED] / [EmergencyCapabilityStatus.UNKNOWN])
 * 2. Operating status ([isOpenNow] == true > null > false)
 * 3. Distance (geographically closer facilities preferred; computes straight-line distance if not precomputed)
 * 4. Verified phone availability (non-blank phone numbers preferred)
 *
 * Never fabricates medical or trauma capabilities.
 */
class HospitalRanker {

    /**
     * Selects the best emergency hospital from [candidates] relative to [riderLocation].
     */
    fun selectBestHospital(
        candidates: List<EmergencyHospital>,
        riderLocation: EmergencyLocation = EmergencyLocation.Unavailable
    ): EmergencyHospitalSelection {
        if (candidates.isEmpty()) {
            return EmergencyHospitalSelection(
                hospital = null,
                reasoning = "No nearby hospital candidates found."
            )
        }

        // Enrich candidates with straight-line distance if missing and location is available
        val enrichedCandidates = candidates.map { candidate ->
            if (candidate.distanceMeters == null && riderLocation is EmergencyLocation.Available) {
                val dist = calculateDistanceMeters(
                    riderLocation.latitude, riderLocation.longitude,
                    candidate.latitude, candidate.longitude
                )
                candidate.copy(distanceMeters = dist)
            } else {
                candidate
            }
        }

        val sorted = enrichedCandidates.sortedWith(
            compareByDescending<EmergencyHospital> { it.emergencyCapabilityStatus == EmergencyCapabilityStatus.VERIFIED }
                .thenByDescending { it.isOpenNow == true }
                .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                .thenByDescending { !it.phoneNumber.isNullOrBlank() }
        )

        val selected = sorted.first()
        val reasoning = buildReasoning(selected)

        return EmergencyHospitalSelection(
            hospital = selected,
            reasoning = reasoning
        )
    }

    private fun buildReasoning(hospital: EmergencyHospital): String {
        val distanceStr = hospital.distanceMeters?.let {
            if (it >= 1000) String.format(java.util.Locale.US, "%.1f km", it / 1000.0)
            else "${it.toInt()} m"
        } ?: "unknown distance"

        return when {
            hospital.emergencyCapabilityStatus == EmergencyCapabilityStatus.VERIFIED ->
                "Selected because emergency capability was verified and it was the closest verified candidate ($distanceStr)."
            hospital.isOpenNow == true ->
                "Selected as the closest verified operational facility ($distanceStr); emergency capability not verified."
            else ->
                "Selected as the closest hospital candidate ($distanceStr); emergency capability not verified."
        }
    }

    companion object {
        /**
         * Calculates straight-line great-circle distance between two coordinates in meters (Haversine formula).
         */
        fun calculateDistanceMeters(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }
    }
}
