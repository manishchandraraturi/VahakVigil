package com.manish.vahakvigil.domain.compiler

import com.manish.vahakvigil.domain.hospital.HospitalDiscoveryProvider
import com.manish.vahakvigil.domain.hospital.HospitalRanker
import com.manish.vahakvigil.domain.location.LocationProvider
import com.manish.vahakvigil.domain.model.EmergencyDispatchContact
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyMedicalSummary
import com.manish.vahakvigil.domain.repository.ContactRepository
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Pure Kotlin data compiler that prepares an [EmergencyDispatchPayload] by reading
 * persisted data from [MedicalProfileRepository] and [ContactRepository],
 * resolving location via optional [LocationProvider], and discovering nearby emergency
 * hospitals via optional [HospitalDiscoveryProvider].
 *
 * Responsibilities:
 * - Read [com.manish.vahakvigil.domain.model.MedicalProfile] from [MedicalProfileRepository]
 * - Read [com.manish.vahakvigil.domain.model.Contact] list from [ContactRepository]
 * - Map medical fields into [EmergencyMedicalSummary] (or null if profile is absent)
 * - Map contacts into [EmergencyDispatchContact] preserving deterministic ordering
 * - Resolve location via provided location snapshot or [LocationProvider] (defaults to [EmergencyLocation.Unavailable])
 * - Discover and rank nearby emergency hospitals if location is available
 * - Preserve incident timestamp
 * - Completely decoupled from Android platform APIs, telephony, SMS, and network calls
 */
class EmergencyDispatchDataCompiler(
    private val medicalProfileRepository: MedicalProfileRepository,
    private val contactRepository: ContactRepository,
    private val locationProvider: LocationProvider? = null,
    private val hospitalDiscoveryProvider: HospitalDiscoveryProvider? = null,
    private val hospitalRanker: HospitalRanker = HospitalRanker()
) {

    /**
     * Compiles an [EmergencyDispatchPayload] for an incident that occurred at [incidentTimestamp].
     *
     * @param incidentTimestamp Timestamp of the qualified emergency candidate incident.
     * @param location Optional explicit location snapshot. If null, queries [locationProvider].
     * @return Deterministic [EmergencyDispatchPayload] containing available stored data and hospital candidate.
     */
    suspend fun compile(
        incidentTimestamp: Long,
        location: EmergencyLocation? = null
    ): EmergencyDispatchPayload {
        val profile = medicalProfileRepository.getProfile().firstOrNull()
        val medicalSummary = profile?.let { EmergencyMedicalSummary.fromMedicalProfile(it) }

        val contacts = contactRepository.getContacts().firstOrNull() ?: emptyList()
        val dispatchContacts = contacts.map { EmergencyDispatchContact.fromContact(it) }

        val resolvedLocation = location ?: locationProvider?.getCurrentLocation() ?: EmergencyLocation.Unavailable

        val hospital = if (resolvedLocation is EmergencyLocation.Available && hospitalDiscoveryProvider != null) {
            try {
                val candidates = hospitalDiscoveryProvider.findNearbyHospitals(resolvedLocation)
                hospitalRanker.selectBestHospital(candidates, resolvedLocation).hospital
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        return EmergencyDispatchPayload(
            incidentTimestamp = incidentTimestamp,
            medicalSummary = medicalSummary,
            emergencyContacts = dispatchContacts,
            location = resolvedLocation,
            hospital = hospital
        )
    }
}
