package com.droidnova.screenrecorder.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingModelsTest {
    @Test fun recordingSettingsHaveDeterministicStructuralEquality() {
        assertEquals(settings(), settings())
        assertNotEquals(settings(), settings().copy(audioMode = AudioMode.Microphone))
    }

    @Test fun recordingStatesHaveDeterministicStructuralEquality() {
        assertEquals(RecordingState.Recording(settings()), RecordingState.Recording(settings()))
        assertEquals(
            RecordingState.Stopping(settings(), StopReason.LowStorage),
            RecordingState.Stopping(settings(), StopReason.LowStorage),
        )
    }

    @Test fun resolutionRequiresPositiveDimensions() {
        assertEquals(CaptureResolution(1, 1), CaptureResolution(1, 1))
        assertThrows(IllegalArgumentException::class.java) { CaptureResolution(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { CaptureResolution(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { CaptureResolution(-1, 1) }
    }

    @Test fun frameRateRequiresPositiveFramesPerSecond() {
        assertEquals(1, FrameRate(1).framesPerSecond)
        assertThrows(IllegalArgumentException::class.java) { FrameRate(0) }
        assertThrows(IllegalArgumentException::class.java) { FrameRate(-1) }
    }

    @Test fun bitrateRequiresPositiveBitsPerSecond() {
        assertEquals(1, VideoBitrate(1).bitsPerSecond)
        assertThrows(IllegalArgumentException::class.java) { VideoBitrate(0) }
        assertThrows(IllegalArgumentException::class.java) { VideoBitrate(-1) }
    }

    @Test fun countdownSupportsNoneAndPositiveDurations() {
        assertEquals(CountdownConfiguration.None, CountdownConfiguration.None)
        listOf(3, 5, 15).forEach { assertEquals(it, CountdownConfiguration.Duration(it).seconds) }
        assertThrows(IllegalArgumentException::class.java) { CountdownConfiguration.Duration(0) }
        assertThrows(IllegalArgumentException::class.java) { CountdownConfiguration.Duration(-1) }
    }

    @Test fun maximumDurationSupportsUnlimitedAndPositiveLimits() {
        assertEquals(MaximumDurationPolicy.Unlimited, MaximumDurationPolicy.Unlimited)
        assertEquals(1L, MaximumDurationPolicy.Limited(1).seconds)
        assertThrows(IllegalArgumentException::class.java) { MaximumDurationPolicy.Limited(0) }
        assertThrows(IllegalArgumentException::class.java) { MaximumDurationPolicy.Limited(-1) }
    }

    @Test fun bitrateRangeRequiresOrderedPositiveBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            VideoBitrateRange(VideoBitrate(2), VideoBitrate(1))
        }
    }

    @Test fun supportedCapabilityCombinationIsAccepted() {
        assertEquals(CapabilityValidation.Supported, capabilities().validate(settings()))
    }

    @Test fun unsupportedResolutionIsTyped() {
        val result = capabilities().validate(settings().copy(resolution = CaptureResolution(1920, 1080)))
        assertEquals(CapabilityValidation.Unsupported(CapabilityRejection.Resolution), result)
    }

    @Test fun unsupportedFrameRateForResolutionIsTyped() {
        val result = capabilities().validate(settings().copy(frameRate = FrameRate(60)))
        assertEquals(CapabilityValidation.Unsupported(CapabilityRejection.FrameRateForResolution), result)
    }

    @Test fun unsupportedBitrateForProfileIsTyped() {
        val result = capabilities().validate(settings().copy(videoBitrate = VideoBitrate(9_000_000)))
        assertEquals(CapabilityValidation.Unsupported(CapabilityRejection.BitrateForProfile), result)
    }

    @Test fun unsupportedAudioModeIsTyped() {
        val result = capabilities().validate(settings().copy(audioMode = AudioMode.DeviceAudio))
        assertEquals(CapabilityValidation.Unsupported(CapabilityRejection.AudioMode), result)
    }

    @Test fun capabilityCollectionsAreDefensivelyCopied() {
        val rates = mutableSetOf(FrameRate(30))
        val profile = VideoCapabilityProfile(CaptureResolution(1280, 720), rates, VideoBitrateRange(VideoBitrate(1), VideoBitrate(2)))
        rates += FrameRate(60)
        assertFalse(FrameRate(60) in profile.supportedFrameRates)

        val profiles = mutableListOf(profile)
        val audio = mutableSetOf(AudioMode.None)
        val capabilities = RecordingCapabilities(profiles, audio, false)
        profiles.clear(); audio += AudioMode.DeviceAudio
        assertEquals(1, capabilities.videoProfiles.size)
        assertEquals(setOf(AudioMode.None), capabilities.supportedAudioModes)
    }

    @Test fun mixedAudioModeDoesNotExist() {
        assertEquals(setOf("None", "Microphone", "DeviceAudio"), AudioMode.entries.map { it.name }.toSet())
        assertFalse(AudioMode.entries.any { "mixed" in it.name.lowercase() })
    }

    @Test fun pauseResumeCapabilityIsExplicit() {
        assertTrue(capabilities().supportsPauseResume)
    }
}

internal fun settings() = RecordingSettings(
    preset = RecordingPreset.Balanced,
    audioMode = AudioMode.None,
    resolution = CaptureResolution(1280, 720),
    frameRate = FrameRate(30),
    videoBitrate = VideoBitrate(4_000_000),
    countdown = CountdownConfiguration.Duration(3),
    maximumDuration = MaximumDurationPolicy.Unlimited,
)

private fun capabilities() = RecordingCapabilities(
    videoProfiles = listOf(
        VideoCapabilityProfile(
            resolution = CaptureResolution(1280, 720),
            supportedFrameRates = setOf(FrameRate(30)),
            bitrateRange = VideoBitrateRange(VideoBitrate(2_000_000), VideoBitrate(6_000_000)),
        ),
    ),
    supportedAudioModes = setOf(AudioMode.None, AudioMode.Microphone),
    supportsPauseResume = true,
)
