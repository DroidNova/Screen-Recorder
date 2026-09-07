package com.droidnova.screenrecorder.feature.home

import com.droidnova.screenrecorder.domain.recording.RecordingState

enum class RecordingControl { Start, Stop, Unavailable }

fun RecordingState.availableRecordingControl(): RecordingControl = when (this) {
    RecordingState.Idle -> RecordingControl.Start
    is RecordingState.Recording -> RecordingControl.Stop
    else -> RecordingControl.Unavailable
}
