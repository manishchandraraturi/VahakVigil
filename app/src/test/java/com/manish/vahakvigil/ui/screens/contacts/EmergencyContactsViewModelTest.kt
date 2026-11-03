package com.manish.vahakvigil.ui.screens.contacts

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.repository.FakeContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with empty contacts list by default`() = runTest(testDispatcher) {
        val repository = FakeContactRepository(emptyList())
        val viewModel = EmergencyContactsViewModel(repository)

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.contacts.isEmpty())
    }

    @Test
    fun `emits updated contacts from repository`() = runTest(testDispatcher) {
        val initialList = listOf(
            Contact(1L, "Ravi", "Brother", "9999999999")
        )
        val repository = FakeContactRepository(initialList)
        val viewModel = EmergencyContactsViewModel(repository)

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.contacts.size)
        assertEquals("Ravi", viewModel.uiState.value.contacts[0].name)

        // Simulate repository update
        repository.saveContact(Contact(2L, "Asha", "Mother", "8888888888"))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.contacts.size)
    }

    @Test
    fun `deleteContact removes contact and updates uiState`() = runTest(testDispatcher) {
        val contact = Contact(1L, "Ravi", "Brother", "9999999999")
        val repository = FakeContactRepository(listOf(contact))
        val viewModel = EmergencyContactsViewModel(repository, ioDispatcher = testDispatcher)

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.contacts.size)

        viewModel.deleteContact(contact)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contacts.isEmpty())
    }

    @Test
    fun `canAddContact is false when 3 contacts exist and true otherwise`() = runTest(testDispatcher) {
        val twoContacts = listOf(
            Contact(1L, "Contact 1", "Friend", "1234567890", 1),
            Contact(2L, "Contact 2", "Sister", "2345678901", 2)
        )
        val repository = FakeContactRepository(twoContacts)
        val viewModel = EmergencyContactsViewModel(repository, ioDispatcher = testDispatcher)

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()
        assertTrue("canAddContact must be true when 2 contacts exist", viewModel.uiState.value.canAddContact)
        org.junit.Assert.assertFalse(viewModel.uiState.value.isMaxContactsReached)

        // Add 3rd contact
        val c3 = Contact(3L, "Contact 3", "Brother", "3456789012", 3)
        repository.saveContact(c3)
        advanceUntilIdle()

        org.junit.Assert.assertFalse("canAddContact must be false when 3 contacts exist", viewModel.uiState.value.canAddContact)
        assertTrue(viewModel.uiState.value.isMaxContactsReached)

        // Delete one contact -> should restore ability to add
        viewModel.deleteContact(c3)
        advanceUntilIdle()

        assertTrue("canAddContact must be restored after deleting down to 2", viewModel.uiState.value.canAddContact)
        org.junit.Assert.assertFalse(viewModel.uiState.value.isMaxContactsReached)
    }

    @Test
    fun `moveContactUp and moveContactDown reorder contacts and update priorities`() = runTest(testDispatcher) {
        val c1 = Contact(1L, "Primary", "Friend", "1234567890", 1)
        val c2 = Contact(2L, "Secondary", "Sister", "2345678901", 2)
        val c3 = Contact(3L, "Tertiary", "Brother", "3456789012", 3)
        val repository = FakeContactRepository(listOf(c1, c2, c3))
        val viewModel = EmergencyContactsViewModel(repository, ioDispatcher = testDispatcher)

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), viewModel.uiState.value.contacts.map { it.id })

        // Move c2 up (swap with c1)
        viewModel.moveContactUp(c2)
        advanceUntilIdle()

        val afterMoveUp = viewModel.uiState.value.contacts
        assertEquals(listOf(2L, 1L, 3L), afterMoveUp.map { it.id })
        assertEquals(1, afterMoveUp[0].priority)
        assertEquals(2, afterMoveUp[1].priority)
        assertEquals(3, afterMoveUp[2].priority)

        // Move c2 down (swap with c1 back)
        viewModel.moveContactDown(afterMoveUp[0])
        advanceUntilIdle()

        val afterMoveDown = viewModel.uiState.value.contacts
        assertEquals(listOf(1L, 2L, 3L), afterMoveDown.map { it.id })
        assertEquals(1, afterMoveDown[0].priority)
        assertEquals(2, afterMoveDown[1].priority)
        assertEquals(3, afterMoveDown[2].priority)
    }

    @Test
    fun `deleteContact normalizes remaining priorities to continuous 1 to N`() = runTest(testDispatcher) {
        val c1 = Contact(1L, "C1", "Friend", "1234567890", 1)
        val c2 = Contact(2L, "C2", "Sister", "2345678901", 2)
        val c3 = Contact(3L, "C3", "Brother", "3456789012", 3)
        val repository = FakeContactRepository(listOf(c1, c2, c3))
        val viewModel = EmergencyContactsViewModel(repository, ioDispatcher = testDispatcher)

        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        // Delete middle contact c2
        viewModel.deleteContact(c2)
        advanceUntilIdle()

        val remaining = viewModel.uiState.value.contacts
        assertEquals(2, remaining.size)
        assertEquals(1L, remaining[0].id)
        assertEquals(1, remaining[0].priority)
        assertEquals(3L, remaining[1].id)
        assertEquals(2, remaining[1].priority) // Was 3, normalized to 2
    }
}
