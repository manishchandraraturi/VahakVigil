package com.manish.vahakvigil.ui.screens.ridehistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.export.SensorCsvDocumentExport
import com.manish.vahakvigil.domain.export.SensorCsvExportProgress
import com.manish.vahakvigil.domain.export.SensorCsvExportResult
import com.manish.vahakvigil.domain.location.RideRouteReplay
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class RideHistoryUiState(
    val sessions: List<RideSession> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val selectedSession: RideSession? = null,
    val selectedPoints: List<RideSessionPoint> = emptyList(),
    val isDetailLoading: Boolean = false,
    val sensorEventCount: Long? = null,
    val sensorCsvExportStatus: SensorCsvExportStatus = SensorCsvExportStatus.IDLE,
    val sensorCsvProgress: SensorCsvExportProgress? = null,
    val sensorCsvError: String? = null
)

enum class SensorCsvExportStatus { IDLE, RUNNING, SUCCEEDED, EMPTY, FAILED, CANCELLED }

class RideHistoryViewModel(
    private val repository: RideSessionRepository,
    private val sensorCsvDocumentExport: SensorCsvDocumentExport
) : ViewModel() {
    private val _state = MutableStateFlow(RideHistoryUiState())
    val state: StateFlow<RideHistoryUiState> = _state.asStateFlow()
    private var sensorCsvExportJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeSessions().catch { _state.value = _state.value.copy(isLoading = false, error = true) }
                .collect { _state.value = _state.value.copy(sessions = it, isLoading = false, error = false) }
        }
    }

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isDetailLoading = true,
                    sensorEventCount = null,
                    sensorCsvExportStatus = SensorCsvExportStatus.IDLE,
                    sensorCsvProgress = null,
                    sensorCsvError = null
                )
            }
            try {
                val session = repository.getSession(id)
                val points = RideRouteReplay.filterReliablePoints(repository.getPoints(id))
                val eventCount = try {
                    sensorCsvDocumentExport.countEvents(id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                _state.update {
                    it.copy(
                        selectedSession = session,
                        selectedPoints = points,
                        isDetailLoading = false,
                        sensorEventCount = eventCount
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.update { it.copy(selectedSession = null, selectedPoints = emptyList(), isDetailLoading = false, error = true) }
            }
        }
    }

    fun exportSensorCsv(sessionId: String, destination: String) {
        if (sensorCsvExportJob?.isActive == true || _state.value.sensorCsvExportStatus == SensorCsvExportStatus.RUNNING) return
        sensorCsvExportJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    sensorCsvExportStatus = SensorCsvExportStatus.RUNNING,
                    sensorCsvProgress = SensorCsvExportProgress(0L, it.sensorEventCount ?: 0L),
                    sensorCsvError = null
                )
            }
            try {
                when (val result = sensorCsvDocumentExport.export(sessionId, destination) { progress ->
                    _state.update { it.copy(sensorCsvProgress = progress) }
                }) {
                    SensorCsvExportResult.Empty -> _state.update {
                        it.copy(sensorCsvExportStatus = SensorCsvExportStatus.EMPTY, sensorCsvProgress = null)
                    }
                    is SensorCsvExportResult.Complete -> _state.update {
                        it.copy(
                            sensorCsvExportStatus = SensorCsvExportStatus.SUCCEEDED,
                            sensorCsvProgress = SensorCsvExportProgress(result.exportedRows, result.exportedRows)
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(sensorCsvExportStatus = SensorCsvExportStatus.CANCELLED) }
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        sensorCsvExportStatus = SensorCsvExportStatus.FAILED,
                        sensorCsvError = error.message?.takeIf(String::isNotBlank)
                    )
                }
            }
        }
    }

    fun cancelSensorCsvExport() {
        sensorCsvExportJob?.cancel()
    }

    fun reportSensorCsvPickerCancelled() {
        if (sensorCsvExportJob?.isActive != true) {
            _state.update { it.copy(sensorCsvExportStatus = SensorCsvExportStatus.CANCELLED) }
        }
    }

    suspend fun getPoints(id: String): List<RideSessionPoint> =
        runCatching { RideRouteReplay.filterReliablePoints(repository.getPoints(id)) }.getOrDefault(emptyList())

    fun clearDetail() {
        _state.update { it.copy(selectedSession = null, selectedPoints = emptyList(), isDetailLoading = false, sensorEventCount = null) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSession(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.update { it.copy(error = true) }
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: RideSessionRepository,
            sensorCsvDocumentExport: SensorCsvDocumentExport
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RideHistoryViewModel(repository, sensorCsvDocumentExport) as T
        }
    }
}
