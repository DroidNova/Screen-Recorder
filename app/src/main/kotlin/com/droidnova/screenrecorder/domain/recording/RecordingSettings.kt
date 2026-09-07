package com.droidnova.screenrecorder.domain.recording

enum class RecordingPreset { DataSaver, Balanced, HighQuality, Custom }

enum class AudioMode { None, Microphone, DeviceAudio }

data class CaptureResolution(val width: Int, val height: Int) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }
}

@JvmInline
value class FrameRate(val framesPerSecond: Int) {
    init { require(framesPerSecond > 0) { "framesPerSecond must be positive" } }
}

@JvmInline
value class VideoBitrate(val bitsPerSecond: Int) {
    init { require(bitsPerSecond > 0) { "bitsPerSecond must be positive" } }
}

sealed interface CountdownConfiguration {
    data object None : CountdownConfiguration
    data class Duration(val seconds: Int) : CountdownConfiguration {
        init { require(seconds > 0) { "countdown seconds must be positive" } }
    }
}

sealed interface MaximumDurationPolicy {
    data object Unlimited : MaximumDurationPolicy
    data class Limited(val seconds: Long) : MaximumDurationPolicy {
        init { require(seconds > 0) { "maximum duration seconds must be positive" } }
    }
}

data class RecordingSettings(
    val preset: RecordingPreset,
    val audioMode: AudioMode,
    val resolution: CaptureResolution,
    val frameRate: FrameRate,
    val videoBitrate: VideoBitrate,
    val countdown: CountdownConfiguration,
    val maximumDuration: MaximumDurationPolicy,
)
