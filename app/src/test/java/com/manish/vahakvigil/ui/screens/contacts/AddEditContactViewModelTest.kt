package com.manish.vahakvigil.ui.screens.contacts

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.repository.FakeContactRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditContactViewModelTest {

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
    fun `validate fails when name is blank`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()
        val viewModel = AddEditContactViewModel(repository, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("")
        viewModel.onRelationshipChanged("Brother")
        viewModel.onPhoneChanged("+919876543210")

        val isValid = viewModel.validate()
        assertFalse(isValid)
        assertEquals("Name cannot be blank", viewModel.uiState.value.nameError)
        assertNull(viewModel.uiState.value.relationshipError)
        assertNull(viewModel.uiState.value.phoneError)
    }

    @Test
    fun `validate fails when relationship is blank`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()
        val viewModel = AddEditContactViewModel(repository, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Ravi Sharma")
        viewModel.onRelationshipChanged("   ")
        viewModel.onPhoneChanged("+919876543210")

        val isValid = viewModel.validate()
        assertFalse(isValid)
        assertNull(viewModel.uiState.value.nameError)
        assertEquals("Relationship cannot be blank", viewModel.uiState.value.relationshipError)
        assertNull(viewModel.uiState.value.phoneError)
    }

    @Test
    fun `validate fails when phone has too few digits`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()
        val viewModel = AddEditContactViewModel(repository, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Ravi Sharma")
        viewModel.onRelationshipChanged("Brother")
        viewModel.onPhoneChanged("12345")

        val isValid = viewModel.validate()
        assertFalse(isValid)
        assertEquals("Phone number must contain 7 to 15 digits", viewModel.uiState.value.phoneError)
    }

    @Test
    fun `validate succeeds with valid formatted phone`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()
        val viewModel = AddEditContactViewModel(repository, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Ravi Sharma")
        viewModel.onRelationshipChanged("Brother")
        viewModel.onPhoneChanged("+91 98765-43210")

        val isValid = viewModel.validate()
        assertTrue(isValid)
        assertNull(viewModel.uiState.value.nameError)
        assertNull(viewModel.uiState.value.relationshipError)
        assertNull(viewModel.uiState.value.phoneError)
    }

    @Test
    fun `saveContact creates new contact in repository and triggers completion`() = runTest(testDispatcher) {
        val repository = FakeContactRepository()
        val viewModel = AddEditContactViewModel(
            repository = repository,
            contactId = 0L,
            ioDispatcher = testDispatcher
        )

        viewModel.onNameChanged("Asha Sharma")
        viewModel.onRelationshipChanged("Mother")
        viewModel.onPhoneChanged("+919876543001")

        viewModel.saveContact()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.isSaveCompleted)

        val saved = repository.getContactById(1L)
        assertNotNull(saved)
        assertEquals("Asha Sharma", saved?.name)
        assertEquals("Mother", saved?.relationship)
        assertEquals("+919876543001", saved?.phone)
    }

    @Test
    fun `loadContact populates fields when editing existing contact`() = runTest(testDispatcher) {
        val existing = Contact(42L, "Dr. Meera Iyer", "Doctor", "+919811024880")
        val repository = FakeContactRepository(listOf(existing))
        val viewModel = AddEditContactViewModel(repository, contactId = 42L, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(42L, state.id)
        assertEquals("Dr. Meera Iyer", state.name)
        assertEquals("Doctor", state.relationship)
        assertEquals("+919811024880", state.phone)
        assertFalse(state.isLoading)
        assertTrue(state.isEditing)
    }

    @Test
    fun `saveContact preserves ID when editing contact`() = runTest(testDispatcher) {
        val existing = Contact(42L, "Dr. Meera Iyer", "Doctor", "+919811024880")
        val repository = FakeContactRepository(listOf(existing))
        val viewModel = AddEditContactViewModel(repository, contactId = 42L, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        viewModel.onRelationshipChanged("Family Doctor")
        viewModel.saveContact()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaveCompleted)

        val updated = repository.getContactById(42L)
        assertNotNull(updated)
        assertEquals(42L, updated?.id)
        assertEquals("Family Doctor", updated?.relationship)
    }

    @Test
    fun `deleteContact removes contact from repository and triggers deletion flag`() = runTest(testDispatcher) {
        val existing = Contact(10L, "John Doe", "Friend", "1234567890")
        val repository = FakeContactRepository(listOf(existing))
        val viewModel = AddEditContactViewModel(repository, contactId = 10L, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        viewModel.deleteContact()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isDeleted)

        val found = repository.getContactById(10L)
        assertNull(found)
    }

    @Test
    fun `saveContact blocks adding 4th contact when 3 contacts already exist`() = runTest(testDispatcher) {
        val threeContacts = listOf(
            Contact(1L, "Contact 1", "Friend", "1234567890", 1),
            Contact(2L, "Contact 2", "Sister", "2345678901", 2),
            Contact(3L, "Contact 3", "Brother", "3456789012", 3)
        )
        val repository = FakeContactRepository(threeContacts)
        val viewModel = AddEditContactViewModel(repository, contactId = 0L, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Contact 4")
        viewModel.onRelationshipChanged("Father")
        viewModel.onPhoneChanged("+919876543000")
        viewModel.saveContact()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Must not complete save", state.isSaveCompleted)
        assertEquals("Maximum 3 emergency contacts reached.", state.generalError)
        assertEquals(3, repository.getContactCount())
    }

    @Test
    fun `saveContact permits editing existing contact when 3 contacts already exist`() = runTest(testDispatcher) {
        val threeContacts = listOf(
            Contact(1L, "Contact 1", "Friend", "1234567890", 1),
            Contact(2L, "Contact 2", "Sister", "2345678901", 2),
            Contact(3L, "Contact 3", "Brother", "3456789012", 3)
        )
        val repository = FakeContactRepository(threeContacts)
        val viewModel = AddEditContactViewModel(repository, contactId = 2L, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        viewModel.onNameChanged("Updated Sister")
        viewModel.saveContact()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Save must complete when editing", state.isSaveCompleted)
        assertNull(state.generalError)
        val updated = repository.getContactById(2L)
        assertEquals("Updated Sister", updated?.name)
    }

    @Test
    fun `saveContact with unique phone number succeeds`() = runTest(testDispatcher) {
        val existing = listOf(
            Contact(1L, "Mom", "Mother", "9876543210", 1)
        )
        val repository = FakeContactRepository(existing)
        val viewModel = AddEditContactViewModel(repository, contactId = 0L, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Dad")
        viewModel.onRelationshipChanged("Father")
        viewModel.onPhoneChanged("1234567890")
        viewModel.saveContact()

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaveCompleted)
        assertNull(viewModel.uiState.value.phoneError)
        assertEquals(2, repository.getContactCount())
    }

    @Test
    fun `saveContact with duplicate phone number is rejected`() = runTest(testDispatcher) {
        val existing = listOf(
            Contact(1L, "Mom", "Mother", "9876543210", 1)
        )
        val repository = FakeContactRepository(existing)
        val viewModel = AddEditContactViewModel(repository, contactId = 0L, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Dad")
        viewModel.onRelationshipChanged("Father")
        viewModel.onPhoneChanged("9876543210")
        viewModel.saveContact()

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaveCompleted)
        assertEquals("A contact with this phone number already exists.", viewModel.uiState.value.phoneError)
        assertEquals(1, repository.getContactCount())
    }

    @Test
    fun `saveContact editing contact to another contacts phone number is rejected`() = runTest(testDispatcher) {
        val existing = listOf(
            Contact(1L, "Mom", "Mother", "9876543210", 1),
            Contact(2L, "Dad", "Father", "1234567890", 2)
        )
        val repository = FakeContactRepository(existing)
        val viewModel = AddEditContactViewModel(repository, contactId = 2L, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        // Attempt to change Dad's phone to Mom's phone
        viewModel.onPhoneChanged("9876543210")
        viewModel.saveContact()

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaveCompleted)
        assertEquals("A contact with this phone number already exists.", viewModel.uiState.value.phoneError)
        val dad = repository.getContactById(2L)
        assertEquals("1234567890", dad?.phone)
    }

    @Test
    fun `saveContact editing contact without changing its own phone number succeeds`() = runTest(testDispatcher) {
        val existing = listOf(
            Contact(1L, "Mom", "Mother", "9876543210", 1)
        )
        val repository = FakeContactRepository(existing)
        val viewModel = AddEditContactViewModel(repository, contactId = 1L, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        viewModel.onNameChanged("Mother Dearest")
        viewModel.saveContact()

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaveCompleted)
        assertNull(viewModel.uiState.value.phoneError)
        val updated = repository.getContactById(1L)
        assertEquals("Mother Dearest", updated?.name)
        assertEquals("9876543210", updated?.phone)
    }

    @Test
    fun `saveContact treats different formatting of same digits as duplicate`() = runTest(testDispatcher) {
        val existing = listOf(
            Contact(1L, "Mom", "Mother", "9876543210", 1)
        )
        val repository = FakeContactRepository(existing)
        val viewModel = AddEditContactViewModel(repository, contactId = 0L, ioDispatcher = testDispatcher)

        viewModel.onNameChanged("Other")
        viewModel.onRelationshipChanged("Family")
        // Different formatting: spaces, hyphens, parentheses
        viewModel.onPhoneChanged("(987) 654-3210")
        viewModel.saveContact()

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaveCompleted)
        assertEquals("A contact with this phone number already exists.", viewModel.uiState.value.phoneError)
        assertEquals(1, repository.getContactCount())
    }
}
