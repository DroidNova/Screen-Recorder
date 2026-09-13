package com.droidnova.screenrecorder.domain.recording

data class VideoBitrateRange(
    val minimum: VideoBitrate,
    val maximum: VideoBitrate,
) {
    init { require(minimum.bitsPerSecond <= maximum.bitsPerSecond) { "minimum must not exceed maximum" } }

    operator fun contains(value: VideoBitrate): Boolean =
        value.bitsPerSecond in minimum.bitsPerSecond..maximum.bitsPerSecond
}

class VideoCapabilityProfile(
    val resolution: CaptureResolution,
    supportedFrameRates: Set<FrameRate>,
    val bitrateRange: VideoBitrateRange,
) {
    val supportedFrameRates: Set<FrameRate> = supportedFrameRates.toSet()

    init { require(this.supportedFrameRates.isNotEmpty()) { "at least one frame rate is required" } }

    override fun equals(other: Any?): Boolean = other is VideoCapabilityProfile &&
        resolution == other.resolution && supportedFrameRates == other.supportedFrameRates && bitrateRange == other.bitrateRange

    override fun hashCode(): Int = 31 * (31 * resolution.hashCode() + supportedFrameRates.hashCode()) + bitrateRange.hashCode()
}

class RecordingCapabilities(
    videoProfiles: Collection<VideoCapabilityProfile>,
    supportedAudioModes: Set<AudioMode>,
    val supportsPauseResume: Boolean,
) {
    val videoProfiles: List<VideoCapabilityProfile> = videoProfiles.toList()
    val supportedAudioModes: Set<AudioMode> = supportedAudioModes.toSet()

    init {
        require(this.videoProfiles.isNotEmpty()) { "at least one video profile is required" }
        require(this.supportedAudioModes.isNotEmpty()) { "at least one audio mode is required" }
    }

    fun validate(settings: RecordingSettings): CapabilityValidation {
        if (settings.audioMode !in supportedAudioModes) {
            return CapabilityValidation.Unsupported(CapabilityRejection.AudioMode)
        }
        val profiles = videoProfiles.filter { it.resolution == settings.resolution }
        if (profiles.isEmpty()) return CapabilityValidation.Unsupported(CapabilityRejection.Resolution)
        if (profiles.none { settings.frameRate in it.supportedFrameRates }) {
            return CapabilityValidation.Unsupported(CapabilityRejection.FrameRateForResolution)
        }
        if (profiles.none { settings.frameRate in it.supportedFrameRates && settings.videoBitrate in it.bitrateRange }) {
            return CapabilityValidation.Unsupported(CapabilityRejection.BitrateForProfile)
        }
        return CapabilityValidation.Supported
    }
}

sealed interface CapabilityValidation {
    data object Supported : CapabilityValidation
    data class Unsupported(val reason: CapabilityRejection) : CapabilityValidation
}

enum class CapabilityRejection { Resolution, FrameRateForResolution, BitrateForProfile, AudioMode }
