package com.droidnova.screenrecorder.domain.recording

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Preparing(val settings: RecordingSettings) : RecordingState
    data class Countdown(val settings: RecordingSettings) : RecordingState
    data class Recording(val settings: RecordingSettings) : RecordingState
    data class Paused(val settings: RecordingSettings) : RecordingState
    data class Stopping(val settings: RecordingSettings, val reason: StopReason) : RecordingState
    data class Completed(val settings: RecordingSettings, val reason: StopReason) : RecordingState
    data class Failed(val settings: RecordingSettings, val failure: RecordingFailure) : RecordingState
}

sealed interface TransitionResult {
    data class Accepted(val previousState: RecordingState, val newState: RecordingState) : TransitionResult
    data class Rejected(
        val state: RecordingState,
        val input: RecordingInput,
        val reason: TransitionRejection,
    ) : TransitionResult
}

enum class TransitionRejection { InputNotAllowedInCurrentState }

object RecordingStateMachine {
    fun transition(state: RecordingState, input: RecordingInput): TransitionResult {
        val next = when (state) {
            RecordingState.Idle -> when (input) {
                is RecordingCommand.Start -> RecordingState.Preparing(input.settings)
                else -> null
            }
            is RecordingState.Preparing -> when (input) {
                RecordingEvent.PreparationReady -> RecordingState.Countdown(state.settings)
                RecordingCommand.Cancel -> RecordingState.Idle
                is RecordingCommand.Stop -> RecordingState.Stopping(state.settings, input.reason)
                is RecordingEvent.FatalFailure -> RecordingState.Failed(state.settings, input.failure)
                else -> null
            }
            is RecordingState.Countdown -> when (input) {
                RecordingEvent.CountdownFinished -> RecordingState.Recording(state.settings)
                RecordingCommand.Cancel -> RecordingState.Stopping(state.settings, StopReason.CountdownCancelled)
                is RecordingCommand.Stop -> RecordingState.Stopping(state.settings, input.reason)
                else -> null
            }
            is RecordingState.Recording -> when (input) {
                RecordingCommand.Pause -> RecordingState.Paused(state.settings)
                is RecordingCommand.Stop -> RecordingState.Stopping(state.settings, input.reason)
                is RecordingEvent.FatalFailure -> RecordingState.Failed(state.settings, input.failure)
                else -> null
            }
            is RecordingState.Paused -> when (input) {
                RecordingCommand.Resume -> RecordingState.Recording(state.settings)
                is RecordingCommand.Stop -> RecordingState.Stopping(state.settings, input.reason)
                is RecordingEvent.FatalFailure -> RecordingState.Failed(state.settings, input.failure)
                else -> null
            }
            is RecordingState.Stopping -> when (input) {
                RecordingEvent.FinalizationSucceeded -> RecordingState.Completed(state.settings, state.reason)
                is RecordingEvent.FinalizationFailed -> RecordingState.Failed(state.settings, input.failure)
                else -> null
            }
            is RecordingState.Completed -> if (input == RecordingCommand.Acknowledge) RecordingState.Idle else null
            is RecordingState.Failed -> if (input == RecordingCommand.Acknowledge) RecordingState.Idle else null
        }
        return if (next == null) {
            TransitionResult.Rejected(state, input, TransitionRejection.InputNotAllowedInCurrentState)
        } else {
            TransitionResult.Accepted(state, next)
        }
    }
}
