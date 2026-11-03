package com.manish.vahakvigil.domain.compiler

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.EmergencyDispatchPayload
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests for [EmergencyDispatchDataCompiler].
 */
class EmergencyDispatchDataCompilerTest {
    @Test
    fun `dispatch payload defaults to no local evidence`() {
        val payload = EmergencyDispatchPayload(
            incidentTimestamp = 123L,
            medicalSummary = null,
            emergencyContacts = emptyList()
        )
        assertNull(payload.localEvidence)
    }


    private lateinit var medicalRepository: FakeMedicalProfileRepository
    private lateinit var contactRepository: FakeContactRepository
    private lateinit var compiler: EmergencyDispatchDataCompiler

    private val sampleProfile = MedicalProfile(
        name = "Manish Sharma",
        age = "29",
        bloodGroup = "O+",
        conditions = "Asthma",
        allergies = "Penicillin",
        medications = "Albuterol inhaler",
        height = "175",
        weight = "72",
        notes = "Contact spouse first"
    )

    private val sampleContact1 = Contact(
        id = 101L,
        name = "Priya Sharma",
        relationship = "Spouse",
        phone = "+919876543210"
    )

    private val sampleContact2 = Contact(
        id = 102L,
        name = "Rajesh Sharma",
        relationship = "Brother",
        phone = "+919876543211"
    )

    @Before
    fun setUp() {
        medicalRepository = FakeMedicalProfileRepository(sampleProfile)
        contactRepository = FakeContactRepository(listOf(sampleContact1, sampleContact2))
        compiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalRepository,
            contactRepository = contactRepository
        )
    }

    // ─── 1. Medical profile + contacts produce correct payload ────────────────

    @Test
    fun `medical profile and contacts produce correct payload`() = runTest {
        val payload = compiler.compile(incidentTimestamp = 1_000_000L)

        assertEquals(1_000_000L, payload.incidentTimestamp)
        assertNotNull(payload.medicalSummary)
        assertEquals("Manish Sharma", payload.medicalSummary?.riderName)
        assertEquals("O+", payload.medicalSummary?.bloodGroup)
        assertEquals(2, payload.emergencyContacts.size)
        assertEquals(EmergencyLocation.Unavailable, payload.location)
    }

    // ─── 2. Missing medical profile produces null medical summary ─────────────

    @Test
    fun `missing medical profile produces null medical summary`() = runTest {
        medicalRepository = FakeMedicalProfileRepository(initialProfile = null)
        compiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)

        val payload = compiler.compile(incidentTimestamp = 2_000_000L)

        assertNull("Medical summary must be null when profile is absent", payload.medicalSummary)
        assertEquals(2, payload.emergencyContacts.size)
    }

    // ─── 3. Empty contacts produce empty contact list ─────────────────────────

    @Test
    fun `empty contacts produce empty contact list`() = runTest {
        contactRepository = FakeContactRepository(initialContacts = emptyList())
        compiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)

        val payload = compiler.compile(incidentTimestamp = 3_000_000L)

        assertTrue("Emergency contacts must be empty when none stored", payload.emergencyContacts.isEmpty())
        assertNotNull(payload.medicalSummary)
    }

    // ─── 4. Stored contacts are preserved correctly ───────────────────────────

    @Test
    fun `stored contacts are preserved correctly`() = runTest {
        val payload = compiler.compile(incidentTimestamp = 4_000_000L)

        val first = payload.emergencyContacts[0]
        assertEquals(sampleContact1.id, first.id)
        assertEquals(sampleContact1.name, first.name)
        assertEquals(sampleContact1.relationship, first.relationship)
        assertEquals(sampleContact1.phone, first.phone)
    }

    // ─── 5. No fake contacts are created ──────────────────────────────────────

    @Test
    fun `no fake contacts are created`() = runTest {
        contactRepository = FakeContactRepository(initialContacts = emptyList())
        compiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)

        val payload = compiler.compile(incidentTimestamp = 5_000_000L)

        assertEquals(0, payload.emergencyContacts.size)
    }

    // ─── 6. Location is UNAVAILABLE because GPS is not implemented ────────────

    @Test
    fun `location is UNAVAILABLE because GPS is not implemented`() = runTest {
        val payload = compiler.compile(incidentTimestamp = 6_000_000L)

        assertEquals(EmergencyLocation.Unavailable, payload.location)
    }

    // ─── 7. No Android APIs are required ──────────────────────────────────────

    @Test
    fun `no Android APIs are required for compilation`() = runTest {
        // Pure JVM execution test
        val payload = compiler.compile(incidentTimestamp = 7_000_000L)
        assertNotNull(payload)
    }

    // ─── 8. Compiler uses repository interfaces ───────────────────────────────

    @Test
    fun `compiler uses repository interfaces`() = runTest {
        // Demonstrates polymorphic injection of repository interfaces
        val customMedical: com.manish.vahakvigil.domain.repository.MedicalProfileRepository = medicalRepository
        val customContact: com.manish.vahakvigil.domain.repository.ContactRepository = contactRepository
        val customCompiler = EmergencyDispatchDataCompiler(customMedical, customContact)

        val payload = customCompiler.compile(incidentTimestamp = 8_000_000L)
        assertNotNull(payload)
    }

    // ─── 9. Payload incident timestamp is preserved ───────────────────────────

    @Test
    fun `payload incident timestamp is preserved`() = runTest {
        val testTimestamp = 987654321L
        val payload = compiler.compile(incidentTimestamp = testTimestamp)

        assertEquals(testTimestamp, payload.incidentTimestamp)
    }

    // ─── 10. Medical fields map correctly from existing MedicalProfile ────────

    @Test
    fun `medical fields map correctly from existing MedicalProfile`() = runTest {
        val payload = compiler.compile(incidentTimestamp = 10_000_000L)
        val summary = payload.medicalSummary
        assertNotNull(summary)

        assertEquals(sampleProfile.name, summary!!.riderName)
        assertEquals(sampleProfile.bloodGroup, summary.bloodGroup)
        assertEquals(sampleProfile.allergies, summary.allergies)
        assertEquals(sampleProfile.conditions, summary.conditions)
        assertEquals(sampleProfile.medications, summary.medications)
        assertEquals(sampleProfile.notes, summary.notes)
    }

    // ─── 11. Contact fields map correctly from existing Contact ───────────────

    @Test
    fun `contact fields map correctly from existing Contact`() = runTest {
        val payload = compiler.compile(incidentTimestamp = 11_000_000L)
        val contact = payload.emergencyContacts.first()

        assertEquals(sampleContact1.id, contact.id)
        assertEquals(sampleContact1.name, contact.name)
        assertEquals(sampleContact1.relationship, contact.relationship)
        assertEquals(sampleContact1.phone, contact.phone)
    }

    // ─── 12. Multiple contacts preserve deterministic ordering ────────────────

    @Test
    fun `multiple contacts preserve deterministic ordering from repository output`() = runTest {
        val contactA = Contact(id = 1L, name = "A", relationship = "R1", phone = "111")
        val contactB = Contact(id = 2L, name = "B", relationship = "R2", phone = "222")
        val contactC = Contact(id = 3L, name = "C", relationship = "R3", phone = "333")

        contactRepository = FakeContactRepository(listOf(contactA, contactB, contactC))
        compiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)

        val payload = compiler.compile(incidentTimestamp = 12_000_000L)

        assertEquals(listOf(1L, 2L, 3L), payload.emergencyContacts.map { it.id })
        assertEquals(listOf("A", "B", "C"), payload.emergencyContacts.map { it.name })
    }

    // ─── 13. Missing data does not cause crashes ──────────────────────────────

    @Test
    fun `missing data does not cause crashes`() = runTest {
        val emptyMedicalRepo = FakeMedicalProfileRepository(initialProfile = null)
        val emptyContactRepo = FakeContactRepository(initialContacts = emptyList())
        val robustCompiler = EmergencyDispatchDataCompiler(emptyMedicalRepo, emptyContactRepo)

        val payload = robustCompiler.compile(incidentTimestamp = 13_000_000L)

        assertNotNull(payload)
        assertNull(payload.medicalSummary)
        assertTrue(payload.emergencyContacts.isEmpty())
        assertEquals(EmergencyLocation.Unavailable, payload.location)
    }

    // ─── 14. Compiler does not perform external actions ───────────────────────

    @Test
    fun `compiler does not perform external actions`() = runTest {
        // Compile does not write or mutate repositories
        compiler.compile(incidentTimestamp = 14_000_000L)

        // Verify underlying data remains unmodified
        assertEquals(sampleProfile, medicalRepository.getProfile().first())
        assertEquals(2, contactRepository.getContacts().first().size)
    }

    // ─── 15. Contacts preserve explicit priority order in payload ─────────────

    @Test
    fun `contacts preserve explicit priority order in compiled payload`() = runTest {
        val tertiary = Contact(id = 30L, name = "Tertiary", relationship = "Friend", phone = "333", priority = 3)
        val primary = Contact(id = 10L, name = "Primary", relationship = "Spouse", phone = "111", priority = 1)
        val secondary = Contact(id = 20L, name = "Secondary", relationship = "Brother", phone = "222", priority = 2)

        // Inject unordered list
        contactRepository = FakeContactRepository(listOf(tertiary, primary, secondary))
        compiler = EmergencyDispatchDataCompiler(medicalRepository, contactRepository)

        val payload = compiler.compile(incidentTimestamp = 15_000_000L)

        assertEquals(3, payload.emergencyContacts.size)
        assertEquals("Primary", payload.emergencyContacts[0].name)
        assertEquals(1, payload.emergencyContacts[0].priority)
        assertEquals("Secondary", payload.emergencyContacts[1].name)
        assertEquals(2, payload.emergencyContacts[1].priority)
        assertEquals("Tertiary", payload.emergencyContacts[2].name)
        assertEquals(3, payload.emergencyContacts[2].priority)
    }
}
