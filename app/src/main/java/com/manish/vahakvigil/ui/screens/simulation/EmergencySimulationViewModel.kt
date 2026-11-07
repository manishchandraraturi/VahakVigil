package com.manish.vahakvigil.ui.screens.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.simulation.EmergencySimulationEvent
import com.manish.vahakvigil.domain.simulation.EmergencySimulationStage
import com.manish.vahakvigil.domain.simulation.EmergencySimulationState
import com.manish.vahakvigil.domain.simulation.EmergencySimulationStateMachine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Demonstration-only ViewModel. It deliberately has no production dispatch dependencies. */
class EmergencySimulationViewModel(
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
    private val suspectedDurationMillis: Long = DEFAULT_SUSPECTED_DURATION_MILLIS,
    private val tickDurationMillis: Long = DEFAULT_TICK_DURATION_MILLIS
) : ViewModel() {
    init {
        require(countdownSeconds > 0)
        require(suspectedDurationMillis >= 0)
        require(tickDurationMillis > 0)
    }

    private val _uiState = MutableStateFlow(EmergencySimulationState())
    val uiState: StateFlow<EmergencySimulationState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun startSimulation() {
        if (_uiState.value.stage != EmergencySimulationStage.INTRO) return
        transition(EmergencySimulationEvent.Start)
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            delay(suspectedDurationMillis)
            if (_uiState.value.stage != EmergencySimulationStage.CRASH_SUSPECTED) return@launch
            transition(EmergencySimulationEvent.CountdownStarted)
            while (isActive && _uiState.value.stage == EmergencySimulationStage.COUNTDOWN) {
                delay(tickDurationMillis)
                if (_uiState.value.stage == EmergencySimulationStage.COUNTDOWN) {
                    transition(EmergencySimulationEvent.Tick)
                }
            }
        }
    }

    fun cancelAsFine() {
        if (_uiState.value.stage == EmergencySimulationStage.COUNTDOWN || _uiState.value.stage == EmergencySimulationStage.CRASH_SUSPECTED) {
            countdownJob?.cancel()
            transition(EmergencySimulationEvent.Cancel)
        }
    }

    fun requestHelp() {
        if (_uiState.value.stage == EmergencySimulationStage.COUNTDOWN || _uiState.value.stage == EmergencySimulationStage.CRASH_SUSPECTED) {
            countdownJob?.cancel()
            transition(EmergencySimulationEvent.RequestHelp)
        }
    }

    fun openBystanderGuidance() = transition(EmergencySimulationEvent.OpenBystanderGuidance)

    fun complete() = transition(EmergencySimulationEvent.Complete)

    fun restart() {
        countdownJob?.cancel()
        countdownJob = null
        transition(EmergencySimulationEvent.Restart)
    }

    private fun transition(event: EmergencySimulationEvent) {
        _uiState.update { EmergencySimulationStateMachine.reduce(it, event, countdownSeconds) }
    }

    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 15
        private const val DEFAULT_SUSPECTED_DURATION_MILLIS = 700L
        private const val DEFAULT_TICK_DURATION_MILLIS = 1_000L
    }
}
