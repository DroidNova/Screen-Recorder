package com.droidnova.screenrecorder.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateMachineTest {
    private val failure = RecordingFailure.VideoEncoderFailure(FailureStage.Runtime)
    private val userStop = RecordingCommand.Stop(StopReason.UserRequested)

    @Test fun everyRequiredValidTransitionIsAccepted() {
        val s = settings()
        val transitions = listOf(
            RecordingState.Idle to RecordingCommand.Start(s) to RecordingState.Preparing(s),
            RecordingState.Preparing(s) to RecordingEvent.PreparationReady to RecordingState.Countdown(s),
            RecordingState.Preparing(s) to RecordingCommand.Cancel to RecordingState.Idle,
            RecordingState.Preparing(s) to RecordingEvent.FatalFailure(failure) to RecordingState.Failed(s, failure),
            RecordingState.Countdown(s) to RecordingEvent.CountdownFinished to RecordingState.Recording(s),
            RecordingState.Countdown(s) to RecordingCommand.Cancel to RecordingState.Stopping(s, StopReason.CountdownCancelled),
            RecordingState.Countdown(s) to userStop to RecordingState.Stopping(s, StopReason.UserRequested),
            RecordingState.Recording(s) to RecordingCommand.Pause to RecordingState.Paused(s),
            RecordingState.Recording(s) to userStop to RecordingState.Stopping(s, StopReason.UserRequested),
            RecordingState.Recording(s) to RecordingEvent.FatalFailure(failure) to RecordingState.Failed(s, failure),
            RecordingState.Paused(s) to RecordingCommand.Resume to RecordingState.Recording(s),
            RecordingState.Paused(s) to userStop to RecordingState.Stopping(s, StopReason.UserRequested),
            RecordingState.Paused(s) to RecordingEvent.FatalFailure(failure) to RecordingState.Failed(s, failure),
            RecordingState.Stopping(s, StopReason.LowStorage) to RecordingEvent.FinalizationSucceeded to RecordingState.Completed(s, StopReason.LowStorage),
            RecordingState.Stopping(s, StopReason.UserRequested) to RecordingEvent.FinalizationFailed(RecordingFailure.FinalizationFailure) to RecordingState.Failed(s, RecordingFailure.FinalizationFailure),
            RecordingState.Completed(s, StopReason.UserRequested) to RecordingCommand.Acknowledge to RecordingState.Idle,
            RecordingState.Failed(s, failure) to RecordingCommand.Acknowledge to RecordingState.Idle,
        )
        transitions.forEach { (stateAndInput, expected) ->
            val (state, input) = stateAndInput
            assertEquals(TransitionResult.Accepted(state, expected), RecordingStateMachine.transition(state, input))
        }
    }

    @Test fun importantInvalidInputForEveryStateIsRejected() {
        val s = settings()
        val cases = listOf(
            RecordingState.Idle to userStop,
            RecordingState.Preparing(s) to RecordingCommand.Pause,
            RecordingState.Countdown(s) to RecordingCommand.Resume,
            RecordingState.Recording(s) to RecordingCommand.Resume,
            RecordingState.Paused(s) to RecordingCommand.Pause,
            RecordingState.Stopping(s, StopReason.UserRequested) to userStop,
            RecordingState.Completed(s, StopReason.UserRequested) to RecordingEvent.FinalizationSucceeded,
            RecordingState.Failed(s, failure) to RecordingEvent.CountdownFinished,
        )
        cases.forEach { (state, input) -> assertRejectedUnchanged(state, input) }
    }

    @Test fun repeatedStopIsRejectedWhileStopping() {
        val stopping = accepted(RecordingState.Recording(settings()), userStop)
        assertRejectedUnchanged(stopping, userStop)
    }

    @Test fun repeatedPauseIsRejectedWhilePaused() {
        val paused = accepted(RecordingState.Recording(settings()), RecordingCommand.Pause)
        assertRejectedUnchanged(paused, RecordingCommand.Pause)
    }

    @Test fun repeatedResumeIsRejectedWhileRecording() {
        val recording = accepted(RecordingState.Paused(settings()), RecordingCommand.Resume)
        assertRejectedUnchanged(recording, RecordingCommand.Resume)
    }

    @Test fun startIsRejectedOutsideIdle() {
        val start = RecordingCommand.Start(settings())
        val activeStates = listOf(
            RecordingState.Preparing(settings()), RecordingState.Countdown(settings()), RecordingState.Recording(settings()),
            RecordingState.Paused(settings()), RecordingState.Stopping(settings(), StopReason.UserRequested),
            RecordingState.Completed(settings(), StopReason.UserRequested), RecordingState.Failed(settings(), failure),
        )
        activeStates.forEach { assertRejectedUnchanged(it, start) }
    }

    @Test fun acknowledgeOnlyRecoversCompletedAndFailed() {
        assertEquals(RecordingState.Idle, accepted(RecordingState.Completed(settings(), StopReason.UserRequested), RecordingCommand.Acknowledge))
        assertEquals(RecordingState.Idle, accepted(RecordingState.Failed(settings(), failure), RecordingCommand.Acknowledge))
        assertRejectedUnchanged(RecordingState.Recording(settings()), RecordingCommand.Acknowledge)
    }

    @Test fun failureRequiresAcknowledgementBeforeNewStart() {
        val failed = accepted(RecordingState.Recording(settings()), RecordingEvent.FatalFailure(failure))
        assertRejectedUnchanged(failed, RecordingCommand.Start(settings()))
        val idle = accepted(failed, RecordingCommand.Acknowledge)
        assertTrue(accepted(idle, RecordingCommand.Start(settings())) is RecordingState.Preparing)
    }

    @Test fun acceptedResultsAreDeterministic() {
        val first = RecordingStateMachine.transition(RecordingState.Idle, RecordingCommand.Start(settings()))
        val second = RecordingStateMachine.transition(RecordingState.Idle, RecordingCommand.Start(settings()))
        assertEquals(first, second)
    }

    @Test fun rejectedResultsAreDeterministicAndRetainSameStateInstance() {
        val state = RecordingState.Paused(settings())
        val first = RecordingStateMachine.transition(state, RecordingCommand.Pause)
        val second = RecordingStateMachine.transition(state, RecordingCommand.Pause)
        assertEquals(first, second)
        assertSame(state, (first as TransitionResult.Rejected).state)
    }

    @Test fun finalizationAndCountdownEventsAreRejectedOutsideTheirStates() {
        assertRejectedUnchanged(RecordingState.Recording(settings()), RecordingEvent.FinalizationSucceeded)
        assertRejectedUnchanged(RecordingState.Preparing(settings()), RecordingEvent.CountdownFinished)
    }

    @Test fun domainStateRequiresNoFileOrOutputIdentifier() {
        val state = accepted(RecordingState.Idle, RecordingCommand.Start(settings()))
        assertEquals(RecordingState.Preparing(settings()), state)
        assertEquals(setOf("settings"), state::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("\$") }.toSet())
    }

    private fun accepted(state: RecordingState, input: RecordingInput): RecordingState =
        (RecordingStateMachine.transition(state, input) as TransitionResult.Accepted).newState

    private fun assertRejectedUnchanged(state: RecordingState, input: RecordingInput) {
        val result = RecordingStateMachine.transition(state, input) as TransitionResult.Rejected
        assertSame(state, result.state)
        assertEquals(TransitionRejection.InputNotAllowedInCurrentState, result.reason)
    }
}
