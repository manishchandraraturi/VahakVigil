package com.manish.vahakvigil.ui.screens.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.data.repository.IncidentEvidenceStore
import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
import com.manish.vahakvigil.domain.repository.EmergencyIncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class EmergencyIncidentUiState(
    val incidents: List<EmergencyIncidentRecord> = emptyList(),
    val selected: EmergencyIncidentRecord? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false
)

class EmergencyIncidentViewModel(
    private val repository: EmergencyIncidentRepository,
    private val evidenceStore: IncidentEvidenceStore
) : ViewModel() {
    private val _state = MutableStateFlow(EmergencyIncidentUiState())
    val state: StateFlow<EmergencyIncidentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeIncidents().catch { _state.value = _state.value.copy(isLoading = false, error = true) }
                .collect { records -> _state.value = _state.value.copy(incidents = records, isLoading = false, error = false) }
        }
    }

    fun load(id: String) {
        viewModelScope.launch {
            try {
                val record = repository.getIncident(id)
                val storedEvidence = id.toLongOrNull()?.let(evidenceStore::read)
                val incident = record?.copy(
                    voiceNote = record.voiceNote ?: storedEvidence?.note,
                    photoReference = record.photoReference ?: storedEvidence?.photo?.absolutePath
                )
                _state.value = _state.value.copy(selected = incident, error = incident == null)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.value = _state.value.copy(selected = null, error = true)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                id.toLongOrNull()?.let(evidenceStore::deleteIncident)
                repository.deleteIncident(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = true)
            }
        }
    }

    companion object {
        fun provideFactory(repository: EmergencyIncidentRepository, evidenceStore: IncidentEvidenceStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EmergencyIncidentViewModel(repository, evidenceStore) as T
            }
    }
}
