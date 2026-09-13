package com.droidnova.screenrecorder.domain.recording

sealed interface RecordingInput

sealed interface RecordingCommand : RecordingInput {
    data class Start(val settings: RecordingSettings) : RecordingCommand
    data object Cancel : RecordingCommand
    data object Pause : RecordingCommand
    data object Resume : RecordingCommand
    data class Stop(val reason: StopReason) : RecordingCommand
    data object Acknowledge : RecordingCommand
}

sealed interface RecordingEvent : RecordingInput {
    data object PreparationReady : RecordingEvent
    data object CountdownFinished : RecordingEvent
    data object FinalizationSucceeded : RecordingEvent
    data class FinalizationFailed(val failure: RecordingFailure.FinalizationFailure) : RecordingEvent
    data class FatalFailure(val failure: RecordingFailure) : RecordingEvent
}

enum class StopReason {
    UserRequested,
    CountdownCancelled,
    MaximumDurationReached,
    LowStorage,
    ProjectionRevoked,
    ScreenLockedOrSystemEnded,
    ThermalProtection,
    ApplicationShutdown,
}

sealed interface RecordingFailure {
    data class InvalidSettings(val reason: InvalidSettingsReason) : RecordingFailure
    data class UnsupportedCapability(val reason: CapabilityRejection) : RecordingFailure
    data object ConsentDenied : RecordingFailure
    data object ConsentUnavailable : RecordingFailure
    data class RequiredPermissionDenied(val permission: RequiredPermission) : RecordingFailure
    data object ProjectionUnavailable : RecordingFailure
    data object ProjectionRevoked : RecordingFailure
    data class VideoEncoderFailure(val stage: FailureStage) : RecordingFailure
    data class AudioFailure(val stage: FailureStage) : RecordingFailure
    data object OutputInitializationFailure : RecordingFailure
    data object StorageUnavailable : RecordingFailure
    data object StorageWriteFailed : RecordingFailure
    data object FinalizationFailure : RecordingFailure
    data object UnexpectedInternalFailure : RecordingFailure
}

enum class InvalidSettingsReason { Resolution, FrameRate, VideoBitrate, Countdown, MaximumDuration }
enum class RequiredPermission { Microphone }
enum class FailureStage { Initialization, Runtime }
