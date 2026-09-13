package com.droidnova.screenrecorder.recording

import com.droidnova.screenrecorder.domain.recording.CaptureResolution
import com.droidnova.screenrecorder.domain.recording.AudioMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoConfigurationTest {
    @Test fun landscapeIsAspectFitWithin720pBound() {
        assertEquals(CaptureResolution(1280, 720), VideoConfigurationSelector.fitAndAlign(1920, 1080, 2, 2))
    }

    @Test fun portraitIsAspectFitWithinPortrait720pBound() {
        assertEquals(CaptureResolution(720, 1280), VideoConfigurationSelector.fitAndAlign(1080, 1920, 2, 2))
    }

    @Test fun smallerSourceIsNotUpscaled() {
        assertEquals(CaptureResolution(640, 360), VideoConfigurationSelector.fitAndAlign(640, 360, 2, 2))
    }

    @Test fun dimensionsAreAlignedDownWithoutStretching() {
        val result = VideoConfigurationSelector.fitAndAlign(1001, 777, 16, 8)
        assertEquals(0, result.width % 16)
        assertEquals(0, result.height % 8)
        assertTrue(result.width <= 1001 && result.height <= 777)
    }

    @Test fun defaultSelectionIsVideoOnly720pThirtyFps() {
        val selected = VideoConfigurationSelector.select(1920, 1080, listOf(candidate("hardware", true)))!!
        assertEquals(CaptureResolution(1280, 720), selected.resolution)
        assertEquals(30, selected.framesPerSecond)
        assertEquals(4_000_000, selected.bitsPerSecond)
    }

    @Test fun milestoneDefaultContainsNoAudio() {
        assertEquals(AudioMode.None, Milestone5Settings.default.audioMode)
    }

    @Test fun hardwareEncoderWinsRegardlessOfInputOrder() {
        val selected = VideoConfigurationSelector.select(1920, 1080, listOf(candidate("software", false), candidate("hardware", true)))!!
        assertEquals("hardware", selected.encoderName)
    }

    @Test fun nameMakesRankingDeterministicWithinHardwareClass() {
        val selected = VideoConfigurationSelector.select(1920, 1080, listOf(candidate("z", true), candidate("a", true)))!!
        assertEquals("a", selected.encoderName)
    }

    @Test fun unsupportedProfileIsRejectedRatherThanIncreased() {
        val unsupported = candidate("encoder", true) { _, _, _, _ -> false }
        assertNull(VideoConfigurationSelector.select(1920, 1080, listOf(unsupported)))
    }

    @Test fun bitrateIsClampedWithinEncoderRange() {
        val selected = VideoConfigurationSelector.select(1920, 1080, listOf(candidate("encoder", true, 1_000_000, 2_000_000)))!!
        assertEquals(2_000_000, selected.bitsPerSecond)
    }

    @Test fun filenameHasSafePrefixAndExactlyOneExtension() {
        val name = RecordingFilename.fromEpochMillis(1_700_000_000_000, 7)
        assertTrue(name.startsWith("ScreenRecording_"))
        assertTrue(name.matches(Regex("[A-Za-z0-9_]+\\.mp4")))
        assertEquals(1, Regex("\\.mp4").findAll(name).count())
    }

    @Test fun filenameCollisionSequenceProducesDifferentNames() {
        assertFalse(RecordingFilename.fromEpochMillis(100, 1) == RecordingFilename.fromEpochMillis(100, 2))
    }

    @Test fun onlyCompleteVideoOutputCanPublish() {
        assertTrue(OutputCompletion(true, 1, true).canPublish())
        assertFalse(OutputCompletion(false, 1, true).canPublish())
        assertFalse(OutputCompletion(true, 0, true).canPublish())
        assertFalse(OutputCompletion(true, 1, false).canPublish())
    }

    private fun candidate(
        name: String,
        hardware: Boolean,
        minimumBitrate: Int = 1_000_000,
        maximumBitrate: Int = 8_000_000,
        supports: (Int, Int, Int, Int) -> Boolean = { _, _, _, _ -> true },
    ) = EncoderCandidate(name, hardware, 2, 2, 64, 1920, 64, 1920, minimumBitrate, maximumBitrate, supports)
}
