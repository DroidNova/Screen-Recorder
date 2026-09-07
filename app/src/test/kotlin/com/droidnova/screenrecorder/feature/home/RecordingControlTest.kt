package com.droidnova.screenrecorder.feature.home

import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.StopReason
import com.droidnova.screenrecorder.domain.recording.settings
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingControlTest {
    @Test fun startExistsOnlyWhenIdleAndStopOnlyWhenRecording() {
        assertEquals(RecordingControl.Start, RecordingState.Idle.availableRecordingControl())
        assertEquals(RecordingControl.Stop, RecordingState.Recording(settings()).availableRecordingControl())
        assertEquals(RecordingControl.Unavailable, RecordingState.Preparing(settings()).availableRecordingControl())
        assertEquals(RecordingControl.Unavailable, RecordingState.Stopping(settings(), StopReason.UserRequested).availableRecordingControl())
    }

    @Test fun pauseAndResumeActionsAreNotExposed() {
        assertEquals(setOf("Start", "Stop", "Unavailable"), RecordingControl.entries.map { it.name }.toSet())
        assertEquals(RecordingControl.Unavailable, RecordingState.Paused(settings()).availableRecordingControl())
    }
}
