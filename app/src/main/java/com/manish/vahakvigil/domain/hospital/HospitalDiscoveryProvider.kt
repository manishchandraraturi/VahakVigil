package com.manish.vahakvigil.domain.hospital

import com.manish.vahakvigil.domain.model.EmergencyHospital
import com.manish.vahakvigil.domain.model.EmergencyLocation

/**
 * Pure Kotlin contract for discovering nearby medical facilities suitable for road accidents.
 */
interface HospitalDiscoveryProvider {

    /**
     * Searches for nearby hospitals relative to [location].
     *
     * Implementations must return an empty list if [location] is [EmergencyLocation.Unavailable],
     * if the network/API is unavailable, or if credentials are unconfigured.
     * Must never throw an unhandled exception or crash the emergency path.
     *
     * @param location Current rider emergency location snapshot.
     * @return List of nearby candidate hospitals.
     */
    suspend fun findNearbyHospitals(location: EmergencyLocation): List<EmergencyHospital>
}

/**
 * Safe default no-op hospital discovery provider.
 * Always returns an empty list without network or hardware calls.
 */
class NoOpHospitalDiscoveryProvider : HospitalDiscoveryProvider {
    override suspend fun findNearbyHospitals(location: EmergencyLocation): List<EmergencyHospital> {
        return emptyList()
    }
}
