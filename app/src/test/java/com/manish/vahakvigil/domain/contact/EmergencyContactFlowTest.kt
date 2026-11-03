package com.manish.vahakvigil.domain.contact

import com.manish.vahakvigil.domain.compiler.EmergencyDispatchDataCompiler
import com.manish.vahakvigil.domain.formatter.EmergencySmsFormatter
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.EmergencyActionResultStatus
import com.manish.vahakvigil.domain.model.EmergencyActionType
import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import com.manish.vahakvigil.domain.model.EmergencyDecision
import com.manish.vahakvigil.domain.model.EmergencyDecisionState
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.orchestration.EmergencyActionOrchestrator
import com.manish.vahakvigil.domain.orchestration.SmsEmergencyActionExecutor
import com.manish.vahakvigil.repository.FakeContactRepository
import com.manish.vahakvigil.repository.FakeMedicalProfileRepository
import com.manish.vahakvigil.sms.FakeEmergencySmsSender
import com.manish.vahakvigil.ui.screens.contacts.AddEditContactUiState
import com.manish.vahakvigil.ui.screens.contacts.AddEditContactViewModel
import com.manish.vahakvigil.ui.screens.contacts.EmergencyContactsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * End-to-end unit tests verifying the clean Emergency Contact flow without OTP verification:
 * 1. Add valid contact -> saves immediately without OTP.
 * 2. Add second contact -> saves.
 * 3. Add third contact -> saves.
 * 4. Attempt fourth contact -> rejected (max 3 contacts).
 * 5. Reorder contacts -> priority preserved.
 * 6. Emergency dispatch -> sends SMS to saved contacts in priority order.
 * 7. No OTP/Firebase Phone Auth code remains reachable from the contact flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyContactFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── 1. Add valid contact -> saves immediately without OTP ─────────────────

    @Test
    fun `1_add valid contact saves immediately without OTP verification`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()
        val viewModel = AddEditContactViewModel(
            repository = repository,
            contactId = 0L,
            ioDispatcher = testDispatcher
        )

        viewModel.onNameChanged("Ravi Sharma")
        viewModel.onRelationshipChanged("Brother")
        viewModel.onPhoneChanged("+919876543210")

        viewModel.saveContact()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Save must complete immediately", state.isSaveCompleted)
        assertFalse(state.isSaving)
        assertNull(state.generalError)

        val savedContacts = repository.getContacts().first()
        assertEquals(1, savedContacts.size)
        assertEquals("Ravi Sharma", savedContacts[0].name)
        assertEquals("Brother", savedContacts[0].relationship)
        assertEquals("+919876543210", savedContacts[0].phone)
        assertEquals(1, savedContacts[0].priority)
        assertTrue(savedContacts[0].isPhoneVerified)
    }

    // ─── 2. Add second contact -> saves ───────────────────────────────────────

    @Test
    fun `2_add second contact saves with next priority`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()

        // First contact
        val vm1 = AddEditContactViewModel(repository = repository, contactId = 0L, ioDispatcher = testDispatcher)
        vm1.onNameChanged("Contact 1")
        vm1.onRelationshipChanged("Father")
        vm1.onPhoneChanged("+919876543001")
        vm1.saveContact()
        advanceUntilIdle()
        assertTrue(vm1.uiState.value.isSaveCompleted)

        // Second contact
        val vm2 = AddEditContactViewModel(repository = repository, contactId = 0L, ioDispatcher = testDispatcher)
        vm2.onNameChanged("Contact 2")
        vm2.onRelationshipChanged("Mother")
        vm2.onPhoneChanged("+919876543002")
        vm2.saveContact()
        advanceUntilIdle()
        assertTrue(vm2.uiState.value.isSaveCompleted)

        val savedContacts = repository.getContacts().first()
        assertEquals(2, savedContacts.size)
        assertEquals("Contact 1", savedContacts[0].name)
        assertEquals(1, savedContacts[0].priority)
        assertEquals("Contact 2", savedContacts[1].name)
        assertEquals(2, savedContacts[1].priority)
    }

    // ─── 3. Add third contact -> saves ────────────────────────────────────────

    @Test
    fun `3_add third contact saves successfully reaching max capacity`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()

        for (i in 1..3) {
            val vm = AddEditContactViewModel(repository = repository, contactId = 0L, ioDispatcher = testDispatcher)
            vm.onNameChanged("Contact $i")
            vm.onRelationshipChanged("Relation $i")
            vm.onPhoneChanged("+91987654300$i")
            vm.saveContact()
            advanceUntilIdle()
            assertTrue("Contact $i must save successfully", vm.uiState.value.isSaveCompleted)
        }

        val savedContacts = repository.getContacts().first()
        assertEquals(3, savedContacts.size)
        assertEquals(listOf(1, 2, 3), savedContacts.map { it.priority })
    }

    // ─── 4. Attempt fourth contact -> rejected (max 3 contacts) ───────────────

    @Test
    fun `4_attempt fourth contact is rejected because max 3 limit is enforced`() = runTest(testDispatcher) {
        val threeContacts = listOf(
            Contact(1L, "Contact 1", "Friend", "+919876543001", priority = 1),
            Contact(2L, "Contact 2", "Sister", "+919876543002", priority = 2),
            Contact(3L, "Contact 3", "Brother", "+919876543003", priority = 3)
        )
        val repository = FakeContactRepository(threeContacts)
        val viewModel = AddEditContactViewModel(repository = repository, contactId = 0L, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Contact 4")
        viewModel.onRelationshipChanged("Colleague")
        viewModel.onPhoneChanged("+919876543004")
        viewModel.saveContact()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Fourth contact must not be saved", state.isSaveCompleted)
        assertEquals("Maximum 3 emergency contacts reached.", state.generalError)
        assertEquals(3, repository.getContactCount())
    }

    // ─── 5. Reorder contacts -> priority preserved ────────────────────────────

    @Test
    fun `5_reorder contacts preserves priorities`() = runTest(testDispatcher) {
        val contactA = Contact(1L, "Alice", "Spouse", "+919876543001", priority = 1)
        val contactB = Contact(2L, "Bob", "Brother", "+919876543002", priority = 2)
        val contactC = Contact(3L, "Charlie", "Friend", "+919876543003", priority = 3)
        val repository = FakeContactRepository(listOf(contactA, contactB, contactC))

        val contactsViewModel = EmergencyContactsViewModel(repository = repository, ioDispatcher = testDispatcher)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            contactsViewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Move Bob up: Bob becomes priority 1, Alice becomes priority 2
        contactsViewModel.moveContactUp(contactB)
        advanceUntilIdle()

        val afterMoveUp = repository.getContacts().first()
        assertEquals("Bob", afterMoveUp[0].name)
        assertEquals(1, afterMoveUp[0].priority)
        assertEquals("Alice", afterMoveUp[1].name)
        assertEquals(2, afterMoveUp[1].priority)
        assertEquals("Charlie", afterMoveUp[2].name)
        assertEquals(3, afterMoveUp[2].priority)

        // Move Bob down: Alice returns to priority 1, Bob returns to priority 2
        contactsViewModel.moveContactDown(contactB)
        advanceUntilIdle()

        val afterMoveDown = repository.getContacts().first()
        assertEquals("Alice", afterMoveDown[0].name)
        assertEquals(1, afterMoveDown[0].priority)
        assertEquals("Bob", afterMoveDown[1].name)
        assertEquals(2, afterMoveDown[1].priority)
        assertEquals("Charlie", afterMoveDown[2].name)
        assertEquals(3, afterMoveDown[2].priority)
    }

    // ─── 6. Emergency dispatch -> sends SMS to saved contacts in priority order ─

    @Test
    fun `6_emergency dispatch sends SMS to saved contacts in priority order`() = runTest(testDispatcher) {
        val contact1 = Contact(10L, "Primary Kin", "Parent", "+919876540001", priority = 1)
        val contact2 = Contact(20L, "Secondary Kin", "Sibling", "+919876540002", priority = 2)
        val contact3 = Contact(30L, "Tertiary Kin", "Friend", "+919876540003", priority = 3)

        val contactRepository = FakeContactRepository(listOf(contact1, contact2, contact3))
        val medicalProfileRepository = FakeMedicalProfileRepository(
            MedicalProfile(
                name = "Rider Test",
                bloodGroup = "B+",
                allergies = "None",
                conditions = "None",
                medications = "None"
            )
        )

        val compiler = EmergencyDispatchDataCompiler(
            medicalProfileRepository = medicalProfileRepository,
            contactRepository = contactRepository
        )

        val payload = compiler.compile(
            incidentTimestamp = 1_700_000_000_000L,
            location = EmergencyLocation.Available(12.9716, 77.5946)
        )

        // Confirm compiled dispatch contacts maintain priority order
        assertEquals(3, payload.emergencyContacts.size)
        assertEquals("+919876540001", payload.emergencyContacts[0].phone)
        assertEquals("+919876540002", payload.emergencyContacts[1].phone)
        assertEquals("+919876540003", payload.emergencyContacts[2].phone)

        val fakeSmsSender = FakeEmergencySmsSender()
        val smsExecutor = SmsEmergencyActionExecutor(
            smsSender = fakeSmsSender,
            smsFormatter = EmergencySmsFormatter()
        )
        val orchestrator = EmergencyActionOrchestrator(actionExecutor = smsExecutor)

        val result = orchestrator.evaluate(
            decision = EmergencyDecision(
                state = EmergencyDecisionState.EMERGENCY_CANDIDATE,
                reason = "Crash detected with sustained stillness",
                incidentTimestampMs = 1_700_000_000_000L,
                postImpactStatus = null
            ),
            countdownState = EmergencyCountdownState.EXPIRED,
            actionType = EmergencyActionType.SMS,
            dispatchPayload = payload
        )

        assertEquals(EmergencyActionResultStatus.TRIGGERED, result.status)

        // Confirm SMS transmission targeted all contacts in exact priority order
        val sentRecords = fakeSmsSender.sentRecords
        assertEquals(3, sentRecords.size)
        assertEquals("+919876540001", sentRecords[0].phoneNumber)
        assertEquals("+919876540002", sentRecords[1].phoneNumber)
        assertEquals("+919876540003", sentRecords[2].phoneNumber)
    }

    // ─── 7. No OTP or Firebase Phone Auth code remains reachable from contact flow ─

    @Test
    fun `7_no OTP or Firebase Phone Auth code remains reachable from contact flow`() {
        // Confirm removed classes are completely absent from classpath
        val removedClasses = listOf(
            "com.manish.vahakvigil.domain.auth.PhoneVerificationProvider",
            "com.manish.vahakvigil.data.auth.AndroidFirebasePhoneVerificationProvider",
            "com.manish.vahakvigil.domain.auth.FakePhoneVerificationProvider",
            "com.manish.vahakvigil.domain.auth.EmergencyContactOtpVerificationTest"
        )

        for (className in removedClasses) {
            try {
                Class.forName(className)
                fail("Class $className should have been deleted!")
            } catch (_: ClassNotFoundException) {
                // Expected
            }
        }

        // Confirm AddEditContactUiState has no OTP fields
        val uiStateFields = AddEditContactUiState::class.java.declaredFields.map { it.name }
        assertFalse("UiState must not have otpInput", uiStateFields.contains("otpInput"))
        assertFalse("UiState must not have verificationState", uiStateFields.contains("verificationState"))
        assertFalse("UiState must not have resendCooldownSeconds", uiStateFields.contains("resendCooldownSeconds"))
        assertFalse("UiState must not have isOtpActive", uiStateFields.contains("isOtpActive"))

        // Confirm AddEditContactViewModel constructor takes only (repository, contactId, ioDispatcher)
        val constructorParamTypes = AddEditContactViewModel::class.java.constructors.first().parameterTypes
        for (paramType in constructorParamTypes) {
            assertFalse(
                "Constructor must not take any phone verification provider",
                paramType.name.contains("Verification") || paramType.name.contains("Auth")
            )
        }
    }
}
