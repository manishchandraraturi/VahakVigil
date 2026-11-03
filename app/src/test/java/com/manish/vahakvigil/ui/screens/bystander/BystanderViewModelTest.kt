package com.manish.vahakvigil.ui.screens.bystander

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BystanderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeContactRepository: FakeContactRepository
    private lateinit var fakeMedicalRepository: FakeMedicalProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeContactRepository = FakeContactRepository(
            listOf(
                Contact(id = 1L, name = "Ravi Sharma", relationship = "Brother", phone = "+919876543210"),
                Contact(id = 2L, name = "Dr. Priya Iyer", relationship = "Doctor", phone = "+919876543211")
            )
        )
        fakeMedicalRepository = FakeMedicalProfileRepository(
            MedicalProfile(
                name = "Manish",
                bloodGroup = "O+",
                allergies = "Penicillin",
                conditions = "Asthma"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiState_loadsContactsAndMedicalProfile() = runTest(testDispatcher) {
        val viewModel = BystanderViewModel(fakeContactRepository, fakeMedicalRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.emergencyContacts.size)
        assertEquals("Ravi Sharma", state.emergencyContacts[0].name)
        assertEquals("O+", state.medicalProfile?.bloodGroup)
        assertEquals("Penicillin", state.medicalProfile?.allergies)
        assertFalse(state.assessment.isAnyAnswered)
        assertTrue(state.guidance.any { it.id == "essential_first_aid" })
    }

    @Test
    fun resolver_notBreathing_generatesCriticalCprGuidance() {
        val guidance = BystanderGuidanceResolver.resolve(
            BystanderAssessment(isBreathing = false)
        )
        val cprItem = guidance.find { it.id == "breathing_critical" }
        assertTrue("Expected breathing_critical item", cprItem != null)
        assertTrue(cprItem!!.isCritical)
        assertEquals(1, cprItem.priority)
        assertTrue(cprItem.bulletPoints.any { it.contains("112") })
        assertTrue(cprItem.bulletPoints.any { it.contains("compressions") })
    }

    @Test
    fun resolver_unconscious_generatesUnconsciousCareGuidance() {
        val guidance = BystanderGuidanceResolver.resolve(
            BystanderAssessment(isConscious = false, isBreathing = true)
        )
        val unconsciousItem = guidance.find { it.id == "unconscious_care" }
        assertTrue("Expected unconscious_care item", unconsciousItem != null)
        assertTrue(unconsciousItem!!.isCritical)
        assertEquals(1, unconsciousItem.priority)
        assertTrue(unconsciousItem.bulletPoints.any { it.contains("Do NOT move") })
    }

    @Test
    fun resolver_heavyBleeding_generatesBleedingControlGuidance() {
        val guidance = BystanderGuidanceResolver.resolve(
            BystanderAssessment(hasHeavyBleeding = true)
        )
        val bleedItem = guidance.find { it.id == "bleeding_control" }
        assertTrue("Expected bleeding_control item", bleedItem != null)
        assertEquals(2, bleedItem!!.priority)
        assertTrue(bleedItem.bulletPoints.any { it.contains("direct pressure") })
    }

    @Test
    fun resolver_seriousInjury_generatesSpinalPrecautionGuidance() {
        val guidance = BystanderGuidanceResolver.resolve(
            BystanderAssessment(hasSeriousInjury = true)
        )
        val spinalItem = guidance.find { it.id == "spinal_precaution" }
        assertTrue("Expected spinal_precaution item", spinalItem != null)
        assertEquals(2, spinalItem!!.priority)
        assertTrue(spinalItem.bulletPoints.any { it.contains("DO NOT remove their helmet") })
    }

    @Test
    fun resolver_allFavorable_generatesStableMonitoringGuidance() {
        val guidance = BystanderGuidanceResolver.resolve(
            BystanderAssessment(
                isConscious = true,
                isBreathing = true,
                hasHeavyBleeding = false,
                hasSeriousInjury = false
            )
        )
        val stableItem = guidance.find { it.id == "stable_monitoring" }
        assertTrue("Expected stable_monitoring item", stableItem != null)
        assertEquals(3, stableItem!!.priority)
    }

    @Test
    fun setAssessmentValues_updatesViewModelState() = runTest(testDispatcher) {
        val viewModel = BystanderViewModel(fakeContactRepository, fakeMedicalRepository)
        advanceUntilIdle()

        viewModel.setBreathing(false)
        assertEquals(false, viewModel.uiState.value.assessment.isBreathing)
        assertTrue(viewModel.uiState.value.guidance.any { it.id == "breathing_critical" })

        viewModel.setConscious(false)
        assertEquals(false, viewModel.uiState.value.assessment.isConscious)

        viewModel.setHeavyBleeding(true)
        assertEquals(true, viewModel.uiState.value.assessment.hasHeavyBleeding)
        assertTrue(viewModel.uiState.value.guidance.any { it.id == "bleeding_control" })

        viewModel.setSeriousInjury(true)
        assertEquals(true, viewModel.uiState.value.assessment.hasSeriousInjury)
        assertTrue(viewModel.uiState.value.guidance.any { it.id == "spinal_precaution" })
    }

    @Test
    fun resetAssessment_clearsSelectionsAndRestoresDefaultGuidance() = runTest(testDispatcher) {
        val viewModel = BystanderViewModel(fakeContactRepository, fakeMedicalRepository)
        advanceUntilIdle()

        viewModel.setBreathing(false)
        viewModel.setHeavyBleeding(true)
        assertTrue(viewModel.uiState.value.assessment.isAnyAnswered)

        viewModel.resetAssessment()
        assertFalse(viewModel.uiState.value.assessment.isAnyAnswered)
        assertNull(viewModel.uiState.value.assessment.isBreathing)
        assertNull(viewModel.uiState.value.assessment.hasHeavyBleeding)
        assertFalse(viewModel.uiState.value.guidance.any { it.id == "breathing_critical" })
    }

    @Test
    fun triggerEmergencyAlert_invokesTriggerCallbackAndUpdatesState() = runTest(testDispatcher) {
        var actionTriggered = false
        val viewModel = BystanderViewModel(
            contactRepository = fakeContactRepository,
            medicalProfileRepository = fakeMedicalRepository,
            emergencyActionTrigger = { actionTriggered = true },
            isInitialAlertTriggered = false
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyAlertTriggered)

        viewModel.triggerEmergencyAlert()

        assertTrue(actionTriggered)
        assertTrue(viewModel.uiState.value.isEmergencyAlertTriggered)
        assertTrue(viewModel.uiState.value.alertStatusMessage?.contains("Emergency") == true)
    }

    @Test
    fun guidanceResolver_sortsGuidanceByPriority() {
        val guidance = BystanderGuidanceResolver.resolve(
            BystanderAssessment(
                isConscious = false,
                isBreathing = false,
                hasHeavyBleeding = true,
                hasSeriousInjury = true
            )
        )
        // Priorities must be non-decreasing
        for (i in 0 until guidance.size - 1) {
            assertTrue(
                "Priority ordering violated: ${guidance[i].priority} > ${guidance[i+1].priority}",
                guidance[i].priority <= guidance[i+1].priority
            )
        }
    }

    @Test
    fun triageStepProgression_walksThroughAllStepsAndSummarizes() = runTest(testDispatcher) {
        val viewModel = BystanderViewModel(fakeContactRepository, fakeMedicalRepository)
        advanceUntilIdle()

        // Initial step is EMERGENCY_PROMPT
        assertEquals(BystanderTriageStep.EMERGENCY_PROMPT, viewModel.uiState.value.currentStep)

        // Start quick check -> CONSCIOUSNESS
        viewModel.startQuickCheck()
        assertEquals(BystanderTriageStep.CONSCIOUSNESS, viewModel.uiState.value.currentStep)

        // Answer consciousness -> BREATHING
        viewModel.answerConsciousness(false)
        assertEquals(BystanderTriageStep.BREATHING, viewModel.uiState.value.currentStep)
        assertEquals(false, viewModel.uiState.value.assessment.isConscious)

        // Answer breathing -> HEAVY_BLEEDING
        viewModel.answerBreathing(false)
        assertEquals(BystanderTriageStep.HEAVY_BLEEDING, viewModel.uiState.value.currentStep)
        assertEquals(false, viewModel.uiState.value.assessment.isBreathing)

        // Answer heavy bleeding -> SERIOUS_INJURY
        viewModel.answerHeavyBleeding(true)
        assertEquals(BystanderTriageStep.SERIOUS_INJURY, viewModel.uiState.value.currentStep)
        assertEquals(true, viewModel.uiState.value.assessment.hasHeavyBleeding)

        // Answer serious injury -> ACTION_SUMMARY
        viewModel.answerSeriousInjury(true)
        assertEquals(BystanderTriageStep.ACTION_SUMMARY, viewModel.uiState.value.currentStep)
        assertEquals(true, viewModel.uiState.value.assessment.hasSeriousInjury)

        // Guidance should contain critical actions
        assertTrue(viewModel.uiState.value.guidance.any { it.isCritical })

        // Reset assessment resets step back to EMERGENCY_PROMPT
        viewModel.resetAssessment()
        assertEquals(BystanderTriageStep.EMERGENCY_PROMPT, viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.assessment.isAnyAnswered)
    }

    @Test
    fun goToStep_directlyNavigatesToRequestedStep() = runTest(testDispatcher) {
        val viewModel = BystanderViewModel(fakeContactRepository, fakeMedicalRepository)
        advanceUntilIdle()

        viewModel.goToStep(BystanderTriageStep.HEAVY_BLEEDING)
        assertEquals(BystanderTriageStep.HEAVY_BLEEDING, viewModel.uiState.value.currentStep)

        viewModel.goToStep(BystanderTriageStep.ACTION_SUMMARY)
        assertEquals(BystanderTriageStep.ACTION_SUMMARY, viewModel.uiState.value.currentStep)
    }
}
