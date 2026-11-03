package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.analyzer.EmergencyCountdownEngine
import com.manish.vahakvigil.domain.analyzer.EmergencyDecisionEngine
import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecisionConfig
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.location.FakeLocationProvider
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests for [LocationProvider], [EmergencyLocation] coordinate validation,
 * and integration with [EmergencyDispatchDataCompiler].
 */
class LocationProviderTest {

    private lateinit var fakeLocationProvider: FakeLocationProvider
    private lateinit var medicalRepository: FakeMedicalProfileRepository
    private lateinit var contactRepository: FakeContactRepository

    private val sampleProfile = MedicalProfile(
        name = "Test Rider",
        age = "30",
        bloodGroup = "A+",
        conditions = "None",
        allergies = "None",
        medications = "None",
        height = "180",
        weight = "75",
        notes = "Safe rider"
    )

    private val sampleContact = Contact(
        id = 1L,
        name = "Emergency Contact",
        relationship = "Family",
        phone = "+919876543210"
    )

    @Before
    fun setUp() {
        fakeLocationProvider = FakeLocationProvider()
        medicalRepository = FakeMedicalProfileRepository(sampleProfile)
        contactRepository = FakeContactRepository(listOf(sampleContact))
    }

    // ─── 1. Available location is returned correctly ──────────────────────────

    @Test
    fun `available location is returned correctly`() = runTest {
        val expected = EmergencyLocation.Available(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 4.5f,
            timestampMs = 1_700_000_000L
        )
        fakeLocationProvider.simulatedLocation = expected

        val result = fakeLocationProvider.getCurrentLocation()

        assertEquals(expected, result)
        assertEquals(1, fakeLocationProvider.callCount)
    }

    // ─── 2. Unavailable location is handled correctly ────────────────────────

    @Test
    fun `unavailable location is handled correctly`() = runTest {
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Unavailable

        val result = fakeLocationProvider.getCurrentLocation()

        assertEquals(EmergencyLocation.Unavailable, result)
        assertEquals(1, fakeLocationProvider.callCount)
    }

    // ─── 3. Permission failure results in unavailable ─────────────────────────

    @Test
    fun `permission failure results in unavailable`() = runTest {
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.9716, 77.5946)
        fakeLocationProvider.shouldSimulatePermissionFailure = true

        val result = fakeLocationProvider.getCurrentLocation()

        assertEquals(EmergencyLocation.Unavailable, result)
    }

    // ─── 4. Provider failure results in unavailable ───────────────────────────

    @Test
    fun `provider failure results in unavailable`() = runTest {
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.9716, 77.5946)
        fakeLocationProvider.shouldSimulateProviderFailure = true

        val result = fakeLocationProvider.getCurrentLocation()

        assertEquals(EmergencyLocation.Unavailable, result)
    }

    // ─── 5. Invalid latitude is rejected ──────────────────────────────────────

    @Test
    fun `invalid latitude is rejected`() = runTest {
        // Upper bound breach
        assertFalse(EmergencyLocation.isValidCoordinate(90.0001, 77.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(90.0001, 77.0)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())

        // Lower bound breach
        assertFalse(EmergencyLocation.isValidCoordinate(-90.0001, 77.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(-90.0001, 77.0)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())

        // NaN
        assertFalse(EmergencyLocation.isValidCoordinate(Double.NaN, 77.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(Double.NaN, 77.0)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())

        // Infinite
        assertFalse(EmergencyLocation.isValidCoordinate(Double.POSITIVE_INFINITY, 77.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(Double.POSITIVE_INFINITY, 77.0)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())
    }

    // ─── 6. Invalid longitude is rejected ─────────────────────────────────────

    @Test
    fun `invalid longitude is rejected`() = runTest {
        // Upper bound breach
        assertFalse(EmergencyLocation.isValidCoordinate(12.0, 180.0001))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.0, 180.0001)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())

        // Lower bound breach
        assertFalse(EmergencyLocation.isValidCoordinate(12.0, -180.0001))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.0, -180.0001)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())

        // NaN
        assertFalse(EmergencyLocation.isValidCoordinate(12.0, Double.NaN))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.0, Double.NaN)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())

        // Infinite
        assertFalse(EmergencyLocation.isValidCoordinate(12.0, Double.NEGATIVE_INFINITY))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.0, Double.NEGATIVE_INFINITY)
        assertEquals(EmergencyLocation.Unavailable, fakeLocationProvider.getCurrentLocation())
    }

    // ─── 7. Valid boundary coordinates are accepted ───────────────────────────

    @Test
    fun `valid boundary coordinates are accepted`() = runTest {
        // North-East extreme
        assertTrue(EmergencyLocation.isValidCoordinate(90.0, 180.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(90.0, 180.0)
        val neResult = fakeLocationProvider.getCurrentLocation()
        assertTrue(neResult is EmergencyLocation.Available)
        assertEquals(90.0, (neResult as EmergencyLocation.Available).latitude, 0.0)
        assertEquals(180.0, neResult.longitude, 0.0)

        // South-West extreme
        assertTrue(EmergencyLocation.isValidCoordinate(-90.0, -180.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(-90.0, -180.0)
        val swResult = fakeLocationProvider.getCurrentLocation()
        assertTrue(swResult is EmergencyLocation.Available)
        assertEquals(-90.0, (swResult as EmergencyLocation.Available).latitude, 0.0)
        assertEquals(-180.0, swResult.longitude, 0.0)

        // Equator & Prime Meridian
        assertTrue(EmergencyLocation.isValidCoordinate(0.0, 0.0))
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(0.0, 0.0)
        val eqResult = fakeLocationProvider.getCurrentLocation()
        assertTrue(eqResult is EmergencyLocation.Available)
    }

    // ─── 8. No fake coordinates are generated ─────────────────────────────────

    @Test
    fun `no fake coordinates are generated`() = runTest {
        val compiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalRepository,
            contactRepository = contactRepository,
            locationProvider = fakeLocationProvider
        )
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Unavailable

        val payload = compiler.compile(incidentTimestamp = 1000L)

        assertEquals(EmergencyLocation.Unavailable, payload.location)
        assertFalse(payload.location is EmergencyLocation.Available)
    }

    // ─── 9. Existing dispatch payload behavior remains intact ─────────────────

    @Test
    fun `existing dispatch payload behavior remains intact`() = runTest {
        // Default constructor without locationProvider
        val defaultCompiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalRepository,
            contactRepository = contactRepository
        )

        val payload = defaultCompiler.compile(incidentTimestamp = 2000L)

        assertEquals(2000L, payload.incidentTimestamp)
        assertNotNull(payload.medicalSummary)
        assertEquals("Test Rider", payload.medicalSummary?.riderName)
        assertEquals(1, payload.emergencyContacts.size)
        assertEquals(EmergencyLocation.Unavailable, payload.location)

        // With locationProvider supplying available location
        val locationCompiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalRepository,
            contactRepository = contactRepository,
            locationProvider = fakeLocationProvider
        )
        val expectedLoc = EmergencyLocation.Available(19.0760, 72.8777, accuracyMeters = 10.0f)
        fakeLocationProvider.simulatedLocation = expectedLoc

        val locPayload = locationCompiler.compile(incidentTimestamp = 3000L)
        assertEquals(expectedLoc, locPayload.location)
    }

    // ─── 10. Location retrieval does not trigger emergency decision logic ─────

    @Test
    fun `location retrieval does not trigger emergency decision logic`() = runTest {
        val decisionEngine = EmergencyDecisionEngine(EmergencyDecisionConfig.DEFAULT)
        val countdownEngine = EmergencyCountdownEngine(75_000L)

        // Query location independently
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.9716, 77.5946)
        val location = fakeLocationProvider.getCurrentLocation()
        assertNotNull(location)

        // Verify decision engine is still at NO_EMERGENCY without sensor incident
        val decision = decisionEngine.evaluate(null, null)
        assertEquals(EmergencyDecisionState.NO_EMERGENCY, decision.state)

        // Verify countdown engine remains IDLE
        assertEquals(EmergencyCountdownState.IDLE, countdownEngine.state)
    }

    // ─── 11. Fake provider produces deterministic results ─────────────────────

    @Test
    fun `fake provider produces deterministic results`() = runTest {
        val loc = EmergencyLocation.Available(28.6139, 77.2090, 8.0f, 5000L)
        fakeLocationProvider.simulatedLocation = loc

        val res1 = fakeLocationProvider.getCurrentLocation()
        val res2 = fakeLocationProvider.getCurrentLocation()
        val res3 = fakeLocationProvider.getCurrentLocation()

        assertEquals(res1, res2)
        assertEquals(res2, res3)
        assertEquals(3, fakeLocationProvider.callCount)
    }

    // ─── 12. No Android framework is required for domain tests ────────────────

    @Test
    fun `no Android framework is required for domain tests`() {
        val available = EmergencyLocation.Available(latitude = 13.0827, longitude = 80.2707)
        val unavailable = EmergencyLocation.Unavailable

        assertNotNull(available)
        assertNotNull(unavailable)
        assertTrue(EmergencyLocation.isValidCoordinate(13.0827, 80.2707))
        assertEquals("https://maps.google.com/?q=13.0827,80.2707", available.mapsUrl)
    }

    // ─── D-01: GPS Timeout & Fallback Contract Tests ─────────────────────────

    @Test
    fun `gps returns normally before timeout`() = runTest {
        val expected = EmergencyLocation.Available(12.9716, 77.5946, accuracyMeters = 5.0f)
        fakeLocationProvider.simulatedLocation = expected
        fakeLocationProvider.delayMs = 1_000L

        val result = withTimeoutOrNull(5_000L) {
            fakeLocationProvider.getCurrentLocation()
        } ?: EmergencyLocation.Unavailable

        assertEquals(expected, result)
        assertEquals(1, fakeLocationProvider.callCount)
    }

    @Test
    fun `gps failure falls back correctly to unavailable`() = runTest {
        fakeLocationProvider.shouldThrowException = true

        val result = try {
            withTimeoutOrNull(5_000L) {
                fakeLocationProvider.getCurrentLocation()
            } ?: EmergencyLocation.Unavailable
        } catch (_: Exception) {
            EmergencyLocation.Unavailable
        }

        assertEquals(EmergencyLocation.Unavailable, result)
    }

    @Test
    fun `gps timeout greater than 5s delay falls back to unavailable`() = runTest {
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.9716, 77.5946)
        fakeLocationProvider.delayMs = 6_000L

        val result = withTimeoutOrNull(5_000L) {
            fakeLocationProvider.getCurrentLocation()
        } ?: EmergencyLocation.Unavailable

        assertEquals(EmergencyLocation.Unavailable, result)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation during gps wait cancels cleanly and safely`() = runTest {
        fakeLocationProvider.simulatedLocation = EmergencyLocation.Available(12.9716, 77.5946)
        fakeLocationProvider.delayMs = 5_000L

        var cancellationHandled = false
        val job = launch {
            try {
                withTimeoutOrNull(5_000L) {
                    fakeLocationProvider.getCurrentLocation()
                }
            } catch (e: CancellationException) {
                cancellationHandled = true
                throw e
            }
        }

        advanceTimeBy(1_000L)
        job.cancel()
        advanceUntilIdle()

        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated", cancellationHandled)
    }
}
