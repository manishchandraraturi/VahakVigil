package com.manish.vahakvigil.ui.screens.medical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Medical Profile screen.
 *
 * Responsibilities:
 * - Owns the single [MedicalProfileUiState] instance as a [StateFlow].
 * - Loads the persisted [MedicalProfile] from Room on initialization using [first],
 *   ensuring active user typing is not clobbered by downstream emissions.
 * - Processes field edits through [updateField].
 * - Persists updates to the local database via [MedicalProfileRepository] on [Dispatchers.IO].
 */
class MedicalProfileViewModel(
    private val repository: MedicalProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalProfileUiState(isLoading = true))
    val uiState: StateFlow<MedicalProfileUiState> = _uiState.asStateFlow()

    init {
        loadPersistedProfile()
    }

    private fun loadPersistedProfile() {
        viewModelScope.launch {
            try {
                val profile = repository.getProfile().first()
                if (profile != null) {
                    _uiState.update { current ->
                        current.copy(
                            name = profile.name,
                            age = profile.age,
                            bloodGroup = profile.bloodGroup,
                            conditions = profile.conditions,
                            allergies = profile.allergies,
                            medications = profile.medications,
                            height = profile.height,
                            weight = profile.weight,
                            notes = profile.notes,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Updates a single field in [uiState].
     */
    fun updateField(field: MedicalField, value: String) {
        _uiState.update { current ->
            when (field) {
                MedicalField.NAME        -> current.copy(name = value)
                MedicalField.AGE         -> current.copy(age = value)
                MedicalField.BLOOD_GROUP -> current.copy(bloodGroup = value)
                MedicalField.CONDITIONS  -> current.copy(conditions = value)
                MedicalField.ALLERGIES   -> current.copy(allergies = value)
                MedicalField.MEDICATIONS -> current.copy(medications = value)
                MedicalField.HEIGHT      -> current.copy(height = value)
                MedicalField.WEIGHT      -> current.copy(weight = value)
                MedicalField.NOTES       -> current.copy(notes = value)
            }
        }
    }

    /**
     * Persists the medical profile to Room on [Dispatchers.IO].
     */
    fun save() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = false) }
            val current = _uiState.value
            val profile = MedicalProfile(
                name = current.name,
                age = current.age,
                bloodGroup = current.bloodGroup,
                conditions = current.conditions,
                allergies = current.allergies,
                medications = current.medications,
                height = current.height,
                weight = current.weight,
                notes = current.notes
            )
            withContext(Dispatchers.IO) {
                repository.saveProfile(profile)
            }
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            delay(2000L)
            _uiState.update { it.copy(saveSuccess = false) }
        }
    }

    companion object {
        fun provideFactory(
            repository: MedicalProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MedicalProfileViewModel(repository) as T
            }
        }
    }
}
