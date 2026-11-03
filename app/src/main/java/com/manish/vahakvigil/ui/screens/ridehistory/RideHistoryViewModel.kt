package com.manish.vahakvigil.ui.screens.ridehistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.location.RideRouteReplay
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class RideHistoryUiState(
    val sessions: List<RideSession> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val selectedSession: RideSession? = null,
    val selectedPoints: List<RideSessionPoint> = emptyList(),
    val isDetailLoading: Boolean = false
)

class RideHistoryViewModel(private val repository: RideSessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(RideHistoryUiState())
    val state: StateFlow<RideHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSessions().catch { _state.value = _state.value.copy(isLoading = false, error = true) }
                .collect { _state.value = _state.value.copy(sessions = it, isLoading = false, error = false) }
        }
    }

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDetailLoading = true)
            try {
                val session = repository.getSession(id)
                val points = RideRouteReplay.filterReliablePoints(repository.getPoints(id))
                _state.value = _state.value.copy(selectedSession = session, selectedPoints = points, isDetailLoading = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.value = _state.value.copy(selectedSession = null, selectedPoints = emptyList(), isDetailLoading = false, error = true)
            }
        }
    }

    suspend fun getPoints(id: String): List<RideSessionPoint> =
        runCatching { RideRouteReplay.filterReliablePoints(repository.getPoints(id)) }.getOrDefault(emptyList())

    fun clearDetail() {
        _state.value = _state.value.copy(selectedSession = null, selectedPoints = emptyList(), isDetailLoading = false)
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSession(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = true)
            }
        }
    }

    companion object {
        fun provideFactory(repository: RideSessionRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RideHistoryViewModel(repository) as T
        }
    }
}
