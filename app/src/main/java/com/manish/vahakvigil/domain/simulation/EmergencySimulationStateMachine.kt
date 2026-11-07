package com.manish.vahakvigil.domain.simulation

/** Local-only states for the demonstrator; these are not production emergency states. */
enum class EmergencySimulationStage {
    INTRO,
    CRASH_SUSPECTED,
    COUNTDOWN,
    USER_CANCELLED,
    HELP_REQUESTED,
    SIMULATED_DISPATCH_COMPLETE,
    BYSTANDER_GUIDANCE,
    COMPLETED
}

data class EmergencySimulationState(
    val stage: EmergencySimulationStage = EmergencySimulationStage.INTRO,
    val remainingSeconds: Int = 0
)

sealed interface EmergencySimulationEvent {
    data object Start : EmergencySimulationEvent
    data object CountdownStarted : EmergencySimulationEvent
    data object Tick : EmergencySimulationEvent
    data object Cancel : EmergencySimulationEvent
    data object RequestHelp : EmergencySimulationEvent
    data object OpenBystanderGuidance : EmergencySimulationEvent
    data object Complete : EmergencySimulationEvent
    data object Restart : EmergencySimulationEvent
}

/** Pure reducer. It has no dispatch, telephony, location, service, or crash-pipeline effects. */
object EmergencySimulationStateMachine {
    fun reduce(
        state: EmergencySimulationState,
        event: EmergencySimulationEvent,
        countdownSeconds: Int
    ): EmergencySimulationState {
        require(countdownSeconds > 0)
        return when (event) {
            EmergencySimulationEvent.Start -> if (state.stage == EmergencySimulationStage.INTRO) {
                EmergencySimulationState(EmergencySimulationStage.CRASH_SUSPECTED, countdownSeconds)
            } else state
            EmergencySimulationEvent.CountdownStarted -> if (state.stage == EmergencySimulationStage.CRASH_SUSPECTED) {
                state.copy(stage = EmergencySimulationStage.COUNTDOWN, remainingSeconds = countdownSeconds)
            } else state
            EmergencySimulationEvent.Tick -> if (state.stage == EmergencySimulationStage.COUNTDOWN) {
                val next = (state.remainingSeconds - 1).coerceAtLeast(0)
                if (next == 0) EmergencySimulationState(EmergencySimulationStage.SIMULATED_DISPATCH_COMPLETE)
                else state.copy(remainingSeconds = next)
            } else state
            EmergencySimulationEvent.Cancel -> if (state.stage == EmergencySimulationStage.COUNTDOWN || state.stage == EmergencySimulationStage.CRASH_SUSPECTED) {
                EmergencySimulationState(EmergencySimulationStage.USER_CANCELLED)
            } else state
            EmergencySimulationEvent.RequestHelp -> if (state.stage == EmergencySimulationStage.COUNTDOWN || state.stage == EmergencySimulationStage.CRASH_SUSPECTED) {
                EmergencySimulationState(EmergencySimulationStage.HELP_REQUESTED)
            } else state
            EmergencySimulationEvent.OpenBystanderGuidance -> if (
                state.stage == EmergencySimulationStage.HELP_REQUESTED ||
                state.stage == EmergencySimulationStage.SIMULATED_DISPATCH_COMPLETE
            ) EmergencySimulationState(EmergencySimulationStage.BYSTANDER_GUIDANCE) else state
            EmergencySimulationEvent.Complete -> if (state.stage == EmergencySimulationStage.BYSTANDER_GUIDANCE) {
                EmergencySimulationState(EmergencySimulationStage.COMPLETED)
            } else state
            EmergencySimulationEvent.Restart -> EmergencySimulationState()
        }
    }
}
