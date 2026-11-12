package com.manish.vahakvigil.ui.screens.ridehistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.analysis.RideSensorAnalysisLoader
import com.manish.vahakvigil.domain.analysis.SensorAnalysisProgress
import com.manish.vahakvigil.domain.location.RecordedRoutePlayback
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.SensorRecordingAnalysis
import com.manish.vahakvigil.domain.export.SensorCsvDocumentExport
import com.manish.vahakvigil.domain.export.SensorCsvExportProgress
import com.manish.vahakvigil.domain.export.SensorCsvExportResult
import com.manish.vahakvigil.domain.location.RideRouteReplay
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
    val sensorAnalysis: SensorRecordingAnalysis? = null,
    val sensorAnalysisLoading: Boolean = false,
    val sensorAnalysisProgress: SensorAnalysisProgress? = null,
    val sensorAnalysisError: Boolean = false,
    val routePlaybackIndex: Int? = null,
    val routeIsPlaying: Boolean = false,
    val sensorCsvExportStatus: SensorCsvExportStatus = SensorCsvExportStatus.IDLE,
    val sensorCsvProgress: SensorCsvExportProgress? = null,
    val sensorCsvError: String? = null
)

enum class SensorCsvExportStatus { IDLE, RUNNING, SUCCEEDED, EMPTY, FAILED, CANCELLED }

class RideHistoryViewModel(
    private val repository: RideSessionRepository,
    private val sensorCsvDocumentExport: SensorCsvDocumentExport,
    sensorEventRepository: SensorEventRepository,
    analysisDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _state = MutableStateFlow(RideHistoryUiState())
    val state: StateFlow<RideHistoryUiState> = _state.asStateFlow()
    private var sensorCsvExportJob: Job? = null
    private val sensorAnalysisLoader = RideSensorAnalysisLoader(sensorEventRepository, dispatcher = analysisDispatcher)
    private var detailLoadJob: Job? = null
    private var sensorAnalysisJob: Job? = null
    private var routePlaybackJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeSessions().catch { _state.value = _state.value.copy(isLoading = false, error = true) }
                .collect { _state.value = _state.value.copy(sessions = it, isLoading = false, error = false) }
        }
    }

    fun loadDetail(id: String) {
        detailLoadJob?.cancel()
        sensorAnalysisJob?.cancel()
        pauseRoutePlayback()
        detailLoadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedSession = null,
                    selectedPoints = emptyList(),
                    isDetailLoading = true,
                    sensorAnalysis = null,
                    sensorAnalysisLoading = true,
                    sensorAnalysisProgress = null,
                    sensorAnalysisError = false,
                    routePlaybackIndex = null,
                    routeIsPlaying = false,
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
                        sensorEventCount = eventCount,
                        routePlaybackIndex = points.firstOrNull()?.let { 0 }
                    )
                }
                loadSensorAnalysis(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.update { it.copy(selectedSession = null, selectedPoints = emptyList(), isDetailLoading = false, sensorAnalysisLoading = false, error = true) }
            }
        }
    }

    private fun loadSensorAnalysis(sessionId: String) {
        sensorAnalysisJob?.cancel()
        sensorAnalysisJob = viewModelScope.launch {
            _state.update { it.copy(sensorAnalysisLoading = true, sensorAnalysisError = false, sensorAnalysisProgress = null) }
            try {
                val analysis = sensorAnalysisLoader.load(sessionId) { progress ->
                    _state.update { it.copy(sensorAnalysisProgress = progress) }
                }
                _state.update {
                    it.copy(sensorAnalysis = analysis, sensorAnalysisLoading = false, sensorAnalysisProgress = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.update { it.copy(sensorAnalysis = null, sensorAnalysisLoading = false, sensorAnalysisProgress = null, sensorAnalysisError = true) }
            }
        }
    }

    fun playRoute() {
        val points = _state.value.selectedPoints
        if (points.isEmpty() || routePlaybackJob?.isActive == true) return
        val lastIndex = points.lastIndex
        val start = _state.value.routePlaybackIndex?.takeIf { it < lastIndex } ?: 0
        _state.update { it.copy(routePlaybackIndex = start, routeIsPlaying = true) }
        routePlaybackJob = viewModelScope.launch {
            var index = start
            while (true) {
                delay(ROUTE_REPLAY_SAMPLE_DELAY_MILLIS)
                val next = RecordedRoutePlayback.nextIndex(index, points.size)
                if (next == null) {
                    _state.update { it.copy(routePlaybackIndex = lastIndex, routeIsPlaying = false) }
                    break
                }
                index = next
                _state.update { it.copy(routePlaybackIndex = index) }
            }
        }
    }

    fun pauseRoutePlayback() {
        routePlaybackJob?.cancel()
        routePlaybackJob = null
        _state.update { if (it.routeIsPlaying) it.copy(routeIsPlaying = false) else it }
    }

    fun seekRoute(progress: Float) {
        pauseRoutePlayback()
        val index = RecordedRoutePlayback.indexForProgress(progress, _state.value.selectedPoints.size)
        _state.update { it.copy(routePlaybackIndex = index) }
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
        detailLoadJob?.cancel()
        sensorAnalysisJob?.cancel()
        pauseRoutePlayback()
        _state.update {
            it.copy(selectedSession = null, selectedPoints = emptyList(), isDetailLoading = false, sensorEventCount = null,
                sensorAnalysis = null, sensorAnalysisLoading = false, sensorAnalysisProgress = null, sensorAnalysisError = false,
                routePlaybackIndex = null, routeIsPlaying = false)
        }
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
        private const val ROUTE_REPLAY_SAMPLE_DELAY_MILLIS = 500L

        fun provideFactory(
            repository: RideSessionRepository,
            sensorCsvDocumentExport: SensorCsvDocumentExport,
            sensorEventRepository: SensorEventRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RideHistoryViewModel(repository, sensorCsvDocumentExport, sensorEventRepository) as T
        }
    }
}
