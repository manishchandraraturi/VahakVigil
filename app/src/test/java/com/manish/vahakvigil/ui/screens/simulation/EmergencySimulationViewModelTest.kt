package com.manish.vahakvigil.ui.screens.simulation

import com.manish.vahakvigil.domain.simulation.EmergencySimulationStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencySimulationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(seconds: Int = 3) = EmergencySimulationViewModel(
        countdownSeconds = seconds,
        suspectedDurationMillis = 500,
        tickDurationMillis = 1_000
    )

    @Test
    fun startSimulation_movesThroughSuspectedToCountdown() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.startSimulation()
        assertEquals(EmergencySimulationStage.CRASH_SUSPECTED, viewModel.uiState.value.stage)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(EmergencySimulationStage.COUNTDOWN, viewModel.uiState.value.stage)
        assertEquals(3, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun countdownProgressesUsingConfiguredTicks() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.startSimulation()
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(EmergencySimulationStage.COUNTDOWN, viewModel.uiState.value.stage)
        assertEquals(2, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun imFine_cancelsCountdownAndItCannotExpireLater() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.startSimulation()
        advanceTimeBy(1_500)
        runCurrent()
        viewModel.cancelAsFine()
        assertEquals(EmergencySimulationStage.USER_CANCELLED, viewModel.uiState.value.stage)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(EmergencySimulationStage.USER_CANCELLED, viewModel.uiState.value.stage)
    }

    @Test
    fun iNeedHelp_stopsCountdownAndShowsOnlyLocalOutcome() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.startSimulation()
        advanceTimeBy(600)
        runCurrent()
        viewModel.requestHelp()
        assertEquals(EmergencySimulationStage.HELP_REQUESTED, viewModel.uiState.value.stage)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(EmergencySimulationStage.HELP_REQUESTED, viewModel.uiState.value.stage)
    }

    @Test
    fun countdownExpiryIsSimulatedAndTransitionsToGuidance() = runTest(dispatcher) {
        val viewModel = createViewModel(seconds = 2)
        viewModel.startSimulation()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(EmergencySimulationStage.SIMULATED_DISPATCH_COMPLETE, viewModel.uiState.value.stage)
        viewModel.openBystanderGuidance()
        assertEquals(EmergencySimulationStage.BYSTANDER_GUIDANCE, viewModel.uiState.value.stage)
    }

    @Test
    fun bystanderGuidanceCanCompleteAndRestartResetsStateAndTimer() = runTest(dispatcher) {
        val viewModel = createViewModel(seconds = 1)
        viewModel.startSimulation()
        advanceTimeBy(1_500)
        runCurrent()
        viewModel.openBystanderGuidance()
        viewModel.complete()
        assertEquals(EmergencySimulationStage.COMPLETED, viewModel.uiState.value.stage)
        viewModel.restart()
        assertEquals(EmergencySimulationStage.INTRO, viewModel.uiState.value.stage)
        assertEquals(0, viewModel.uiState.value.remainingSeconds)
        viewModel.startSimulation()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(EmergencySimulationStage.COUNTDOWN, viewModel.uiState.value.stage)
    }

    @Test
    fun duplicateStartDoesNotCreateOrResetCountdown() = runTest(dispatcher) {
        val viewModel = createViewModel(seconds = 4)
        viewModel.startSimulation()
        viewModel.startSimulation()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(4, viewModel.uiState.value.remainingSeconds)
        advanceTimeBy(1_000)
        runCurrent()
        viewModel.startSimulation()
        assertEquals(EmergencySimulationStage.COUNTDOWN, viewModel.uiState.value.stage)
        assertEquals(3, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun helpRequestProducesOnlyTheLocalSimulationOutcomeAndHasNoDispatchDependency() {
        val unchanged = com.manish.vahakvigil.domain.simulation.EmergencySimulationStateMachine.reduce(
            com.manish.vahakvigil.domain.simulation.EmergencySimulationState(EmergencySimulationStage.COUNTDOWN, 1),
            com.manish.vahakvigil.domain.simulation.EmergencySimulationEvent.RequestHelp,
            countdownSeconds = 15
        )
        assertEquals(EmergencySimulationStage.HELP_REQUESTED, unchanged.stage)
        assertEquals(0, unchanged.remainingSeconds)

        val timingOnlyConstructor = EmergencySimulationViewModel::class.java.declaredConstructors.any { constructor ->
            constructor.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType))
        }
        assertTrue("Simulation may only inject countdown timing, never a production dispatcher", timingOnlyConstructor)
        val forbiddenDependencies = EmergencySimulationViewModel::class.java.declaredFields.map { it.type.name }
            .filter { type -> type.contains("EmergencyActionOrchestrator") || type.contains("EmergencySmsSender") }
        assertFalse("Simulation ViewModel must not hold production dispatch dependencies: $forbiddenDependencies", forbiddenDependencies.isNotEmpty())
    }
}
