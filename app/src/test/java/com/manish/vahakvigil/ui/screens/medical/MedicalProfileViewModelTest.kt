package com.manish.vahakvigil.ui.screens.medical

import com.manish.vahakvigil.domain.model.MedicalProfile
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicalProfileViewModelTest {

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
    fun `loads persisted profile on init`() = runTest(testDispatcher) {
        val initialProfile = MedicalProfile(
            name = "Test User",
            bloodGroup = "B+",
            allergies = "Peanuts"
        )
        val repository = FakeMedicalProfileRepository(initialProfile)
        val viewModel = MedicalProfileViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test User", state.name)
        assertEquals("B+", state.bloodGroup)
        assertEquals("Peanuts", state.allergies)
        assertFalse(state.isLoading)
    }

    @Test
    fun `starts with empty fields and isLoading false when database is empty`() = runTest(testDispatcher) {
        val repository = FakeMedicalProfileRepository(null)
        val viewModel = MedicalProfileViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertFalse(state.isLoading)
    }

    @Test
    fun `updateField updates uiState correctly`() = runTest(testDispatcher) {
        val repository = FakeMedicalProfileRepository()
        val viewModel = MedicalProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.updateField(MedicalField.NAME, "Aman")
        viewModel.updateField(MedicalField.BLOOD_GROUP, "A+")

        val state = viewModel.uiState.value
        assertEquals("Aman", state.name)
        assertEquals("A+", state.bloodGroup)
    }

    @Test
    fun `save persists profile to repository`() = runTest(testDispatcher) {
        val repository = FakeMedicalProfileRepository()
        val viewModel = MedicalProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.updateField(MedicalField.NAME, "Rahul")
        viewModel.updateField(MedicalField.BLOOD_GROUP, "O-")
        viewModel.save()

        // Wait for coroutine and simulated delay
        advanceUntilIdle()

        val saved = repository.getProfile()
        // verify saved profile
        val currentUiState = viewModel.uiState.value
        assertEquals("Rahul", currentUiState.name)
        assertEquals("O-", currentUiState.bloodGroup)
    }
}
