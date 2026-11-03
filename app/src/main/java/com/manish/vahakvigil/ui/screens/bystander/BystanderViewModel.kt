package com.manish.vahakvigil.ui.screens.bystander

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.repository.ContactRepository
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the Bystander screen assessment and guidance state.
 *
 * Connects the UI to [ContactRepository] and [MedicalProfileRepository] to display
 * the rider's trusted emergency contacts and critical medical info to an on-scene bystander,
 * and maintains the dynamic assessment inputs with deterministic guidance from [BystanderGuidanceResolver].
 */
class BystanderViewModel(
    private val contactRepository: ContactRepository,
    private val medicalProfileRepository: MedicalProfileRepository,
    private val emergencyActionTrigger: (() -> Unit)? = null,
    isInitialAlertTriggered: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BystanderUiState(
            isEmergencyAlertTriggered = isInitialAlertTriggered,
            alertStatusMessage = if (isInitialAlertTriggered) "Emergency SMS alert requested for trusted contacts." else null
        )
    )
    val uiState: StateFlow<BystanderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            contactRepository.getContacts().collect { contacts ->
                _uiState.update { it.copy(emergencyContacts = contacts) }
            }
        }

        viewModelScope.launch {
            medicalProfileRepository.getProfile().collect { profile ->
                _uiState.update { it.copy(medicalProfile = profile) }
            }
        }
    }

    fun setConscious(conscious: Boolean) {
        updateAssessment { it.copy(isConscious = conscious) }
    }

    fun setBreathing(breathing: Boolean) {
        updateAssessment { it.copy(isBreathing = breathing) }
    }

    fun setHeavyBleeding(bleeding: Boolean) {
        updateAssessment { it.copy(hasHeavyBleeding = bleeding) }
    }

    fun setSeriousInjury(seriousInjury: Boolean) {
        updateAssessment { it.copy(hasSeriousInjury = seriousInjury) }
    }

    fun startQuickCheck() {
        _uiState.update { it.copy(currentStep = BystanderTriageStep.CONSCIOUSNESS) }
    }

    fun answerConsciousness(conscious: Boolean) {
        setConscious(conscious)
        _uiState.update { it.copy(currentStep = BystanderTriageStep.BREATHING) }
    }

    fun answerBreathing(breathing: Boolean) {
        setBreathing(breathing)
        _uiState.update { it.copy(currentStep = BystanderTriageStep.HEAVY_BLEEDING) }
    }

    fun answerHeavyBleeding(bleeding: Boolean) {
        setHeavyBleeding(bleeding)
        _uiState.update { it.copy(currentStep = BystanderTriageStep.SERIOUS_INJURY) }
    }

    fun answerSeriousInjury(seriousInjury: Boolean) {
        setSeriousInjury(seriousInjury)
        _uiState.update { it.copy(currentStep = BystanderTriageStep.ACTION_SUMMARY) }
    }

    fun goToStep(step: BystanderTriageStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun resetAssessment() {
        val defaultAssessment = BystanderAssessment()
        _uiState.update {
            it.copy(
                currentStep = BystanderTriageStep.EMERGENCY_PROMPT,
                assessment = defaultAssessment,
                guidance = BystanderGuidanceResolver.resolve(defaultAssessment)
            )
        }
    }

    fun triggerEmergencyAlert() {
        if (!_uiState.value.isEmergencyAlertTriggered) {
            emergencyActionTrigger?.invoke()
            _uiState.update {
                it.copy(
                    isEmergencyAlertTriggered = true,
                    alertStatusMessage = "Emergency SMS alert requested for trusted contacts."
                )
            }
        }
    }

    private fun updateAssessment(transform: (BystanderAssessment) -> BystanderAssessment) {
        val updated = transform(_uiState.value.assessment)
        val guidance = BystanderGuidanceResolver.resolve(updated)
        _uiState.update {
            it.copy(
                assessment = updated,
                guidance = guidance
            )
        }
    }

    companion object {
        fun provideFactory(
            contactRepository: ContactRepository,
            medicalProfileRepository: MedicalProfileRepository,
            emergencyActionTrigger: (() -> Unit)? = null,
            isInitialAlertTriggered: Boolean = false
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BystanderViewModel(
                    contactRepository = contactRepository,
                    medicalProfileRepository = medicalProfileRepository,
                    emergencyActionTrigger = emergencyActionTrigger,
                    isInitialAlertTriggered = isInitialAlertTriggered
                ) as T
            }
        }
    }
}
