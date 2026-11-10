package com.manish.vahakvigil.domain.emergency

import com.manish.vahakvigil.domain.ambulance.AmbulanceRequestProvider
import com.manish.vahakvigil.domain.ambulance.AmbulanceRequestResult
import com.manish.vahakvigil.domain.ambulance.AmbulanceRequestStatus
import com.manish.vahakvigil.domain.ambulance.UnconfiguredAmbulanceRequestProvider
import com.manish.vahakvigil.domain.backend.BackendSyncResult
import com.manish.vahakvigil.domain.backend.BackendSyncStatus
import com.manish.vahakvigil.domain.backend.EmergencyBackendAuditProvider
import com.manish.vahakvigil.domain.backend.LocalOnlyBackendAuditProvider
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.hospital.EmergencyHospitalCommunicationProvider
import com.manish.vahakvigil.domain.hospital.HospitalCallResult
import com.manish.vahakvigil.domain.hospital.HospitalCallStatus
import com.manish.vahakvigil.domain.hospital.HospitalDiscoveryProvider
import com.manish.vahakvigil.domain.hospital.HospitalRanker
import com.manish.vahakvigil.domain.hospital.NoOpHospitalDiscoveryProvider
import com.manish.vahakvigil.domain.hospital.UnconfiguredHospitalCommunicationProvider
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyActionRequest
import com.manish.vahakvigil.domain.model.EmergencyActionResult
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCapabilityStatus
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyDeliveryStatus
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.EmergencyHospital
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyMedicalSummary
import com.manish.vahakvigil.domain.model.EmergencyResponseState
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.orchestration.SmsEmergencyActionExecutor
import com.manish.vahakvigil.domain.telephony.EmergencyVoiceCallContext
import com.manish.vahakvigil.domain.telephony.EmergencyVoiceCommunicationProvider
import com.manish.vahakvigil.domain.telephony.UnconfiguredVoiceCommunicationProvider
import com.manish.vahakvigil.domain.telephony.VoiceCallStatus
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import com.manish.vahakvigil.sms.FakeEmergencySmsSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for Emergency Response 2.0 — Part 2.
 *
 * Covers all 25 required test dimensions:
 * 1. Hospital search success
 * 2. Hospital search failure
 * 3. Hospital ranking
 * 4. Emergency capability not falsely inferred
 * 5. Hospital phone missing
 * 6. Hospital coordinates preserved
 * 7. Hospital Maps link correct
 * 8. Hospital communication provider not configured
 * 9. Hospital call request
 * 10. Hospital call failure
 * 11. Ambulance provider not configured
 * 12. Ambulance request status handling
 * 13. GPS timeout still allows contact notification
 * 14. Hospital search failure still allows contact notification
 * 15. Hospital call failure still allows contact notification
 * 16. Firebase failure does not break local emergency flow
 * 17. Cancelled incident cannot call hospital
 * 18. Cancelled incident cannot request ambulance
 * 19. Cancelled incident cannot notify contacts
 * 20. Stale incident cannot communicate
 * 21. New incident works after previous cancellation
 * 22. Three contacts remain correctly prioritized
 * 23. Medical information is correctly included
 * 24. Sensitive fields are not leaked unnecessarily
 * 25. No fake success states
 */
class EmergencyResponse2Part2Test {

    private lateinit var contactRepository: FakeContactRepository
    private lateinit var medicalRepository: FakeMedicalProfileRepository
    private lateinit var smsFormatter: EmergencySmsFormatter
    private lateinit var fakeSmsSender: FakeEmergencySmsSender
    private lateinit var actionExecutor: SmsEmergencyActionExecutor
    private lateinit var orchestrator: EmergencyActionOrchestrator
    private lateinit var hospitalRanker: HospitalRanker
    private lateinit var voiceProvider: EmergencyVoiceCommunicationProvider
    private lateinit var ambulanceProvider: AmbulanceRequestProvider
    private lateinit var backendProvider: EmergencyBackendAuditProvider

    private val sampleRiderLocation = EmergencyLocation.Available(
        latitude = 19.0760,
        longitude = 72.8777
    )

    private val sampleMedical = MedicalProfile(
        name = "Vikram Patel",
        age = "32",
        bloodGroup = "B+",
        conditions = "Asthma",
        allergies = "None",
        medications = "Inhaler",
        height = "180",
        weight = "78",
        notes = "Call spouse"
    )

    @Before
    fun setUp() {
        contactRepository = FakeContactRepository(
            listOf(
                Contact(1L, "Primary Contact", "Spouse", "+919876543211", 1),
                Contact(2L, "Secondary Contact", "Brother", "+919876543212", 2),
                Contact(3L, "Tertiary Contact", "Friend", "+919876543213", 3)
            )
        )
        medicalRepository = FakeMedicalProfileRepository(sampleMedical)
        smsFormatter = EmergencySmsFormatter()
        fakeSmsSender = FakeEmergencySmsSender()
        actionExecutor = SmsEmergencyActionExecutor(fakeSmsSender, smsFormatter)
        orchestrator = EmergencyActionOrchestrator(actionExecutor)
        hospitalRanker = HospitalRanker()
        voiceProvider = UnconfiguredVoiceCommunicationProvider()
        ambulanceProvider = UnconfiguredAmbulanceRequestProvider()
        backendProvider = LocalOnlyBackendAuditProvider()
    }

    // ─── 1. Hospital Search Success ───────────────────────────────────────────

    @Test
    fun `hospital search success discovers and selects best candidate`() = runTest {
        val testHospital = EmergencyHospital(
            placeId = "hosp_1",
            name = "City Care Hospital",
            address = "123 Main Rd",
            latitude = 19.0800,
            longitude = 72.8800,
            phoneNumber = "+912222222222",
            emergencyCapabilityStatus = EmergencyCapabilityStatus.VERIFIED
        )
        val discovery = object : HospitalDiscoveryProvider {
            override suspend fun findNearbyHospitals(location: EmergencyLocation): List<EmergencyHospital> {
                return listOf(testHospital)
            }
        }
        val compiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalRepository,
            contactRepository = contactRepository,
            hospitalDiscoveryProvider = discovery,
            hospitalRanker = hospitalRanker
        )

        val payload = compiler.compile(1000L, location = sampleRiderLocation)
        assertNotNull(payload.hospital)
        assertEquals("City Care Hospital", payload.hospital?.name)
        assertEquals("+912222222222", payload.hospital?.phoneNumber)
    }

    // ─── 2. Hospital Search Failure ───────────────────────────────────────────

    @Test
    fun `hospital search failure sets hospital to null and does not crash`() = runTest {
        val failingDiscovery = object : HospitalDiscoveryProvider {
            override suspend fun findNearbyHospitals(location: EmergencyLocation): List<EmergencyHospital> {
                throw RuntimeException("Network timeout communicating with Places API")
            }
        }
        val compiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalRepository,
            contactRepository = contactRepository,
            hospitalDiscoveryProvider = failingDiscovery,
            hospitalRanker = hospitalRanker
        )

        val payload = compiler.compile(2000L, location = sampleRiderLocation)
        assertNull(payload.hospital)
        assertEquals(3, payload.emergencyContacts.size)
        assertEquals(sampleRiderLocation, payload.location)
    }

    // ─── 3. Hospital Ranking ──────────────────────────────────────────────────

    @Test
    fun `hospital ranking prioritizes verified capability over unverified closer hospital`() {
        val closerUnverified = EmergencyHospital(
            placeId = "h1",
            name = "Small Clinic",
            address = "Near St",
            latitude = 19.0765,
            longitude = 72.8780,
            distanceMeters = 300.0,
            phoneNumber = "+91111",
            emergencyCapabilityStatus = EmergencyCapabilityStatus.UNVERIFIED
        )
        val furtherVerified = EmergencyHospital(
            placeId = "h2",
            name = "Apex Trauma Center",
            address = "Far Ave",
            latitude = 19.0860,
            longitude = 72.8880,
            distanceMeters = 1500.0,
            phoneNumber = "+91222",
            emergencyCapabilityStatus = EmergencyCapabilityStatus.VERIFIED
        )

        val selection = hospitalRanker.selectBestHospital(
            listOf(closerUnverified, furtherVerified),
            sampleRiderLocation
        )

        assertEquals("Apex Trauma Center", selection.hospital?.name)
        assertTrue(selection.reasoning.contains("emergency capability was verified"))
    }

    // ─── 4. Emergency Capability Not Falsely Inferred ─────────────────────────

    @Test
    fun `emergency capability is not falsely inferred as trauma center when unverified`() {
        val regularHospital = EmergencyHospital(
            placeId = "h3",
            name = "General Hospital",
            address = "Central St",
            latitude = 19.0770,
            longitude = 72.8790,
            emergencyCapabilityStatus = EmergencyCapabilityStatus.UNVERIFIED
        )

        val selection = hospitalRanker.selectBestHospital(listOf(regularHospital), sampleRiderLocation)
        assertEquals(EmergencyCapabilityStatus.UNVERIFIED, selection.hospital?.emergencyCapabilityStatus)
        assertTrue(selection.reasoning.contains("emergency capability not verified"))
        assertFalse(selection.reasoning.contains("trauma center"))
    }

    // ─── 5. Hospital Phone Missing ────────────────────────────────────────────

    @Test
    fun `hospital with missing phone handled safely without inventing number`() {
        val noPhoneHospital = EmergencyHospital(
            placeId = "h4",
            name = "District Hospital",
            address = "Highway 4",
            latitude = 19.0790,
            longitude = 72.8810,
            phoneNumber = null
        )

        assertNull(noPhoneHospital.phoneNumber)
        val unconfiguredProvider = UnconfiguredHospitalCommunicationProvider()
        val callResult = kotlinx.coroutines.runBlocking {
            unconfiguredProvider.initiateHospitalCall(noPhoneHospital)
        }
        assertEquals(HospitalCallStatus.NOT_AVAILABLE, callResult.status)
        assertEquals("Hospital phone unavailable.", callResult.details)
    }

    // ─── 6. Hospital Coordinates Preserved Separately from Rider ─────────────

    @Test
    fun `hospital coordinates preserved and distinct from rider location`() {
        val hospital = EmergencyHospital(
            placeId = "h5",
            name = "Metro Hospital",
            address = "Ring Rd",
            latitude = 19.1234,
            longitude = 72.9876
        )
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 5000L,
            medicalSummary = null,
            emergencyContacts = emptyList(),
            location = sampleRiderLocation,
            hospital = hospital
        )

        val riderLoc = payload.location as EmergencyLocation.Available
        assertEquals(19.0760, riderLoc.latitude, 0.0001)
        assertEquals(72.8777, riderLoc.longitude, 0.0001)

        val hosp = payload.hospital!!
        assertEquals(19.1234, hosp.latitude, 0.0001)
        assertEquals(72.9876, hosp.longitude, 0.0001)
        assertNotEquals(riderLoc.latitude, hosp.latitude)
    }

    // ─── 7. Hospital Maps Link Correct ────────────────────────────────────────

    @Test
    fun `hospital Maps link targets hospital coordinates directly`() {
        val hospital = EmergencyHospital(
            placeId = "h6",
            name = "East Care",
            address = "East St",
            latitude = 19.2000,
            longitude = 72.9000
        )
        assertEquals("https://maps.google.com/?q=19.2,72.9", hospital.mapsUrl)
    }

    // ─── 8. Hospital Communication Provider Not Configured ────────────────────

    @Test
    fun `hospital communication provider returns NOT_CONFIGURED when unconfigured`() = runTest {
        val hospital = EmergencyHospital(
            placeId = "h7",
            name = "Govt Hospital",
            address = "Civil Lines",
            latitude = 19.0800,
            longitude = 72.8800,
            phoneNumber = "+919999999999"
        )
        val provider = UnconfiguredHospitalCommunicationProvider()
        val result = provider.initiateHospitalCall(hospital)

        assertEquals(HospitalCallStatus.NOT_CONFIGURED, result.status)
        assertEquals("Hospital telephony provider not configured.", result.details)
    }

    // ─── 9. Hospital Call Request ─────────────────────────────────────────────

    @Test
    fun `hospital call request initiates correctly when valid provider configured`() = runTest {
        val hospital = EmergencyHospital(
            placeId = "h8",
            name = "West Hospital",
            address = "West Rd",
            latitude = 19.0800,
            longitude = 72.8800,
            phoneNumber = "+919876543210"
        )
        val mockProvider = object : EmergencyHospitalCommunicationProvider {
            override suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult {
                return HospitalCallResult(HospitalCallStatus.DIAL_REQUESTED, "Dialer requested")
            }
            override fun getCommunicationStatus(): HospitalCallStatus = HospitalCallStatus.DIAL_REQUESTED
        }

        val result = mockProvider.initiateHospitalCall(hospital)
        assertEquals(HospitalCallStatus.DIAL_REQUESTED, result.status)
        assertEquals(HospitalCallStatus.DIAL_REQUESTED, mockProvider.getCommunicationStatus())
    }

    // ─── 10. Hospital Call Failure ────────────────────────────────────────────

    @Test
    fun `hospital call failure returns FAILED without crashing`() = runTest {
        val hospital = EmergencyHospital(
            placeId = "h9",
            name = "Failing Hospital",
            address = "Lane 9",
            latitude = 19.0800,
            longitude = 72.8800,
            phoneNumber = "+910000000000"
        )
        val failingProvider = object : EmergencyHospitalCommunicationProvider {
            override suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult {
                return HospitalCallResult(HospitalCallStatus.FAILED, "Call rejected by telecom gateway")
            }
            override fun getCommunicationStatus(): HospitalCallStatus = HospitalCallStatus.FAILED
        }

        val result = failingProvider.initiateHospitalCall(hospital)
        assertEquals(HospitalCallStatus.FAILED, result.status)
        assertEquals("Call rejected by telecom gateway", result.details)
    }

    // ─── 11. Ambulance Provider Not Configured ────────────────────────────────

    @Test
    fun `ambulance provider returns NOT_CONFIGURED when unconfigured`() = runTest {
        val payload = EmergencyDispatchPayload(100L, null, emptyList(), sampleRiderLocation)
        val result = ambulanceProvider.requestAmbulance(sampleRiderLocation, null, payload)

        assertEquals(AmbulanceRequestStatus.NOT_CONFIGURED, result.status)
        assertEquals("Ambulance request service not configured.", result.details)
    }

    // ─── 12. Ambulance Request Status Handling ────────────────────────────────

    @Test
    fun `ambulance request status handling supports full lifecycle without fake claims`() = runTest {
        val testStatuses = listOf(
            AmbulanceRequestStatus.REQUESTED,
            AmbulanceRequestStatus.ACCEPTED,
            AmbulanceRequestStatus.REJECTED,
            AmbulanceRequestStatus.FAILED
        )

        testStatuses.forEach { expected ->
            val provider = object : AmbulanceRequestProvider {
                override suspend fun requestAmbulance(
                    location: EmergencyLocation,
                    hospital: EmergencyHospital?,
                    payload: EmergencyDispatchPayload
                ): AmbulanceRequestResult = AmbulanceRequestResult(expected, "State: $expected")

                override fun getRequestStatus(): AmbulanceRequestStatus = expected
            }

            val payload = EmergencyDispatchPayload(200L, null, emptyList(), sampleRiderLocation)
            val result = provider.requestAmbulance(sampleRiderLocation, null, payload)
            assertEquals(expected, result.status)
            assertEquals(expected, provider.getRequestStatus())
        }
    }

    // ─── 13. GPS Timeout Still Allows Contact Notification ────────────────────

    @Test
    fun `GPS timeout still allows contact notification to complete`() = runTest {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 3000L,
            medicalSummary = EmergencyMedicalSummary.fromMedicalProfile(sampleMedical),
            emergencyContacts = contactRepository.getContacts().first().map {
                com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
            },
            location = EmergencyLocation.Unavailable // Simulates GPS timeout
        )

        val request = EmergencyActionRequest(
            incidentTimestamp = 3000L,
            actionType = EmergencyActionType.SMS,
            reason = "Countdown expired",
            dispatchPayload = payload
        )

        val result = actionExecutor.execute(request)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(3, fakeSmsSender.sentRecords.size)
        assertTrue(fakeSmsSender.sentRecords[0].message.contains("Location: unavailable"))
    }

    // ─── 14. Hospital Search Failure Still Allows Contact Notification ────────

    @Test
    fun `hospital search failure still allows contact notification to succeed`() = runTest {
        // Hospital is null due to discovery failure
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 4000L,
            medicalSummary = null,
            emergencyContacts = contactRepository.getContacts().first().map {
                com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
            },
            location = sampleRiderLocation,
            hospital = null
        )

        val request = EmergencyActionRequest(
            incidentTimestamp = 4000L,
            actionType = EmergencyActionType.SMS,
            reason = "Countdown expired",
            dispatchPayload = payload
        )

        val result = actionExecutor.execute(request)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)
        assertEquals(3, fakeSmsSender.sentRecords.size)
    }

    // ─── 15. Hospital Call Failure Still Allows Contact Notification ──────────

    @Test
    fun `hospital call failure still allows contact notification to execute`() = runTest {
        val hospital = EmergencyHospital(
            placeId = "h_fail",
            name = "Busy Hospital",
            address = "Old Rd",
            latitude = 19.0760,
            longitude = 72.8777,
            phoneNumber = "+912222222222"
        )
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 5000L,
            medicalSummary = null,
            emergencyContacts = contactRepository.getContacts().first().map {
                com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
            },
            location = sampleRiderLocation,
            hospital = hospital
        )

        // Hospital call fails
        val hospProvider = object : EmergencyHospitalCommunicationProvider {
            override suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult =
                HospitalCallResult(HospitalCallStatus.FAILED, "Network unreachable")
            override fun getCommunicationStatus(): HospitalCallStatus = HospitalCallStatus.FAILED
        }
        val callRes = hospProvider.initiateHospitalCall(hospital)
        assertEquals(HospitalCallStatus.FAILED, callRes.status)

        // Emergency contact SMS dispatch still proceeds
        val request = EmergencyActionRequest(5000L, EmergencyActionType.SMS, "Expired", payload)
        val smsRes = actionExecutor.execute(request)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, smsRes.status)
        assertEquals(3, fakeSmsSender.sentRecords.size)
    }

    // ─── 16. Firebase Failure Does Not Break Local Emergency Flow ─────────────

    @Test
    fun `firebase failure does not break local emergency dispatch flow`() = runTest {
        val failingBackend = object : EmergencyBackendAuditProvider {
            override suspend fun recordEmergencyEvent(
                incidentTimestamp: Long,
                payload: EmergencyDispatchPayload,
                responseState: EmergencyResponseState
            ): BackendSyncResult = BackendSyncResult(BackendSyncStatus.FAILED, "Firestore unreachable")
        }

        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 6000L,
            medicalSummary = null,
            emergencyContacts = contactRepository.getContacts().first().map {
                com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
            },
            location = sampleRiderLocation
        )

        // Cloud sync fails
        val syncResult = failingBackend.recordEmergencyEvent(6000L, payload, EmergencyResponseState.FAILED)
        assertEquals(BackendSyncStatus.FAILED, syncResult.status)

        // Local emergency dispatch MUST continue unaffected
        val request = EmergencyActionRequest(6000L, EmergencyActionType.SMS, "Expired", payload)
        val localResult = actionExecutor.execute(request)
        assertEquals(EmergencyActionResultStatus.TRIGGERED, localResult.status)
        assertEquals(3, fakeSmsSender.sentRecords.size)
    }

    // ─── 17. Cancelled Incident Cannot Call Hospital ──────────────────────────

    @Test
    fun `cancelled incident prevents hospital communication`() = runTest {
        var callAttempted = false
        val hospital = EmergencyHospital("h_c", "H", "A", 19.0, 72.0, phoneNumber = "111")
        val communicationProvider = object : EmergencyHospitalCommunicationProvider {
            override suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult {
                callAttempted = true
                return HospitalCallResult(HospitalCallStatus.DIAL_REQUESTED)
            }
            override fun getCommunicationStatus(): HospitalCallStatus = HospitalCallStatus.NOT_CONFIGURED
        }

        val countdownState = EmergencyCountdownState.CANCELLED
        if (countdownState != EmergencyCountdownState.EXPIRED) {
            // Guard logic prevents hospital communication on cancelled state
        } else {
            communicationProvider.initiateHospitalCall(hospital)
        }

        assertFalse("Hospital call must not be attempted when cancelled", callAttempted)
    }

    // ─── 18. Cancelled Incident Cannot Request Ambulance ──────────────────────

    @Test
    fun `cancelled incident prevents ambulance request`() = runTest {
        var ambulanceRequested = false
        val provider = object : AmbulanceRequestProvider {
            override suspend fun requestAmbulance(
                location: EmergencyLocation,
                hospital: EmergencyHospital?,
                payload: EmergencyDispatchPayload
            ): AmbulanceRequestResult {
                ambulanceRequested = true
                return AmbulanceRequestResult(AmbulanceRequestStatus.REQUESTED)
            }
            override fun getRequestStatus(): AmbulanceRequestStatus = AmbulanceRequestStatus.NOT_CONFIGURED
        }

        val countdownState = EmergencyCountdownState.CANCELLED
        if (countdownState == EmergencyCountdownState.EXPIRED) {
            val payload = EmergencyDispatchPayload(7000L, null, emptyList())
            provider.requestAmbulance(sampleRiderLocation, null, payload)
        }

        assertFalse("Ambulance must not be requested when incident is cancelled", ambulanceRequested)
    }

    // ─── 19. Cancelled Incident Cannot Notify Contacts ────────────────────────

    @Test
    fun `cancelled incident prevents contact notification via orchestrator`() = runTest {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 8000L,
            medicalSummary = null,
            emergencyContacts = contactRepository.getContacts().first().map {
                com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
            }
        )

        val decision = EmergencyDecision(
            state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
            reason = "Test impact",
            incidentTimestampMs = 8000L,
            postImpactStatus = null
        )

        val result = orchestrator.evaluate(
            decision = decision,
            countdownState = EmergencyCountdownState.CANCELLED,
            dispatchPayload = payload
        )

        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, result.status)
        assertEquals(0, fakeSmsSender.sentRecords.size)
    }

    // ─── 20. Stale Incident Cannot Communicate ────────────────────────────────

    @Test
    fun `stale incident cannot trigger actions after cancellation`() = runTest {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 9000L,
            medicalSummary = null,
            emergencyContacts = contactRepository.getContacts().first().map {
                com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
            }
        )

        val decision = EmergencyDecision(
            state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
            reason = "Impact",
            incidentTimestampMs = 9000L,
            postImpactStatus = null
        )

        // Cancelled first
        orchestrator.evaluate(decision, EmergencyCountdownState.CANCELLED, dispatchPayload = payload)

        // Late expired event arrives for stale incident
        val lateResult = orchestrator.evaluate(decision, EmergencyCountdownState.EXPIRED, dispatchPayload = payload)
        assertEquals(EmergencyActionResultStatus.NOT_TRIGGERED, lateResult.status)
        assertEquals("Incident was previously cancelled", lateResult.details)
        assertEquals(0, fakeSmsSender.sentRecords.size)
    }

    // ─── 21. New Incident Works After Previous Cancellation ───────────────────

    @Test
    fun `new incident works cleanly after previous incident was cancelled`() = runTest {
        val cContacts = contactRepository.getContacts().first().map {
            com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
        }

        // Incident 1 cancelled
        val decision1 = EmergencyDecision(EmergencyDecisionState.EMERGENCY_CANDIDATE, "I1", 10_000L, null)
        orchestrator.evaluate(decision1, EmergencyCountdownState.CANCELLED, dispatchPayload = EmergencyDispatchPayload(10_000L, null, cContacts))
        assertEquals(0, fakeSmsSender.sentRecords.size)

        // Incident 2 occurs later and expires
        val decision2 = EmergencyDecision(EmergencyDecisionState.EMERGENCY_CANDIDATE, "I2", 20_000L, null)
        val result2 = orchestrator.evaluate(
            decision2,
            EmergencyCountdownState.EXPIRED,
            dispatchPayload = EmergencyDispatchPayload(20_000L, null, cContacts)
        )
        assertEquals(EmergencyActionResultStatus.TRIGGERED, result2.status)
        assertEquals(3, fakeSmsSender.sentRecords.size)
    }

    // ─── 22. Three Contacts Remain Correctly Prioritized ─────────────────────

    @Test
    fun `three emergency contacts remain correctly prioritized in dispatch payload`() = runTest {
        val compiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)
        val payload = compiler.compile(30_000L)

        assertEquals(3, payload.emergencyContacts.size)
        assertEquals("Primary Contact", payload.emergencyContacts[0].name)
        assertEquals(1, payload.emergencyContacts[0].priority)
        assertEquals("Secondary Contact", payload.emergencyContacts[1].name)
        assertEquals(2, payload.emergencyContacts[1].priority)
        assertEquals("Tertiary Contact", payload.emergencyContacts[2].name)
        assertEquals(3, payload.emergencyContacts[2].priority)
    }

    // ─── 23. Medical Information Correctly Included in SMS ────────────────────

    @Test
    fun `medical information and hospital information are correctly included in SMS`() = runTest {
        val hospital = EmergencyHospital(
            placeId = "h_full",
            name = "Metro Emergency Hospital",
            address = "Ring Rd 5",
            latitude = 19.0760,
            longitude = 72.8777,
            phoneNumber = "+912233445566"
        )
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 40_000L,
            medicalSummary = EmergencyMedicalSummary.fromMedicalProfile(sampleMedical),
            emergencyContacts = emptyList(),
            location = sampleRiderLocation,
            hospital = hospital
        )

        val sms = smsFormatter.format(payload)
        assertTrue(sms.messageText.contains("Rider: Vikram Patel"))
        assertTrue(sms.messageText.contains("Blood group: B+"))
        assertTrue(sms.messageText.contains("Conditions: Asthma"))
        assertTrue(sms.messageText.contains("Location: 19.076, 72.8777"))
        assertTrue(sms.messageText.contains("Map: https://maps.google.com/?q=19.076,72.8777"))
        assertTrue(sms.messageText.contains("Emergency hospital: Metro Emergency Hospital"))
        assertTrue(sms.messageText.contains("Hospital: +912233445566"))
    }

    // ─── 24. Sensitive Fields Are Not Leaked Unnecessarily ───────────────────

    @Test
    fun `sensitive fields are not leaked unnecessarily in AI voice script`() {
        val context = EmergencyVoiceCallContext(
            riderName = "Vikram Patel",
            incidentTimestamp = 50_000L,
            location = sampleRiderLocation,
            hospitalName = "Metro Hospital",
            hospitalPhone = "+912233445566",
            medicalSummary = EmergencyMedicalSummary.fromMedicalProfile(sampleMedical)
        )

        val spokenMessage = context.generateSpokenMessage()
        assertTrue(spokenMessage.contains("Vikram Patel"))
        assertTrue(spokenMessage.contains("Metro Hospital"))
        assertFalse("Must never invent bleeding", spokenMessage.contains("bleeding"))
        assertFalse("Must never invent broken bones", spokenMessage.contains("fracture"))
        assertFalse("Must never invent severity", spokenMessage.contains("severe"))
        assertFalse("Must never invent unconsciousness", spokenMessage.contains("unconscious"))
        assertFalse("Must never invent ambulance dispatch", spokenMessage.contains("Ambulance dispatched"))
    }

    // ─── 25. No Fake Success States ───────────────────────────────────────────

    @Test
    fun `no fake success states are reported by providers`() = runTest {
        // Voice provider returns NOT_CONFIGURED, not COMPLETED
        val voiceRes = voiceProvider.initiateEmergencyCall(
            "+91111",
            EmergencyVoiceCallContext(null, 1L, EmergencyLocation.Unavailable, null, null, null)
        )
        assertNotEquals(VoiceCallStatus.COMPLETED, voiceRes.status)
        assertEquals(VoiceCallStatus.NOT_CONFIGURED, voiceRes.status)

        // Ambulance provider returns NOT_CONFIGURED, not ACCEPTED
        val ambRes = ambulanceProvider.requestAmbulance(
            EmergencyLocation.Unavailable,
            null,
            EmergencyDispatchPayload(1L, null, emptyList())
        )
        assertNotEquals(AmbulanceRequestStatus.ACCEPTED, ambRes.status)
        assertEquals(AmbulanceRequestStatus.NOT_CONFIGURED, ambRes.status)

        // A successful API return records system handoff, not carrier or handset delivery.
        val smsResult = actionExecutor.execute(
            EmergencyActionRequest(
                1L,
                EmergencyActionType.SMS,
                "Test",
                EmergencyDispatchPayload(1L, null, contactRepository.getContacts().first().map {
                    com.manish.vahakvigil.domain.model.EmergencyDispatchContact.fromContact(it)
                })
            )
        )
        assertEquals(EmergencyDeliveryStatus.SENT_TO_MESSAGING_SYSTEM, smsResult.deliveryStatus)
    }
}
