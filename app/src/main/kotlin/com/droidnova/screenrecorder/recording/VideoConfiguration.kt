package com.droidnova.screenrecorder.recording

import com.droidnova.screenrecorder.domain.recording.CaptureResolution
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.CountdownConfiguration
import com.droidnova.screenrecorder.domain.recording.FrameRate
import com.droidnova.screenrecorder.domain.recording.MaximumDurationPolicy
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.domain.recording.RecordingSettings
import com.droidnova.screenrecorder.domain.recording.VideoBitrate

data class EncoderCandidate(
    val name: String,
    val hardwareAccelerated: Boolean,
    val widthAlignment: Int,
    val heightAlignment: Int,
    val minimumWidth: Int,
    val maximumWidth: Int,
    val minimumHeight: Int,
    val maximumHeight: Int,
    val minimumBitsPerSecond: Int,
    val maximumBitsPerSecond: Int,
    val supports: (width: Int, height: Int, framesPerSecond: Int, bitsPerSecond: Int) -> Boolean,
)

data class SelectedVideoConfiguration(
    val encoderName: String,
    val resolution: CaptureResolution,
    val framesPerSecond: Int,
    val bitsPerSecond: Int,
)

object VideoConfigurationSelector {
    const val DEFAULT_FRAMES_PER_SECOND = 30
    const val DEFAULT_BITS_PER_SECOND = 4_000_000

    fun select(sourceWidth: Int, sourceHeight: Int, candidates: List<EncoderCandidate>): SelectedVideoConfiguration? {
        require(sourceWidth > 0 && sourceHeight > 0)
        return candidates.sortedWith(compareByDescending<EncoderCandidate> { it.hardwareAccelerated }.thenBy { it.name })
            .firstNotNullOfOrNull { candidate ->
                val bitrate = DEFAULT_BITS_PER_SECOND.coerceIn(candidate.minimumBitsPerSecond, candidate.maximumBitsPerSecond)
                val initial = fitAndAlign(sourceWidth, sourceHeight, candidate.widthAlignment, candidate.heightAlignment)
                generateSequence(initial) { previous ->
                    val width = previous.width - candidate.widthAlignment
                    val height = alignDown((width.toDouble() * sourceHeight / sourceWidth).toInt(), candidate.heightAlignment)
                    if (width < candidate.minimumWidth || height < candidate.minimumHeight) null else CaptureResolution(width, height)
                }.firstOrNull { size ->
                    size.width in candidate.minimumWidth..candidate.maximumWidth &&
                        size.height in candidate.minimumHeight..candidate.maximumHeight &&
                        candidate.supports(size.width, size.height, DEFAULT_FRAMES_PER_SECOND, bitrate)
                }?.let { SelectedVideoConfiguration(candidate.name, it, DEFAULT_FRAMES_PER_SECOND, bitrate) }
            }
    }

    fun fitAndAlign(sourceWidth: Int, sourceHeight: Int, widthAlignment: Int, heightAlignment: Int): CaptureResolution {
        require(sourceWidth > 0 && sourceHeight > 0 && widthAlignment > 0 && heightAlignment > 0)
        val landscape = sourceWidth >= sourceHeight
        val maxWidth = if (landscape) 1280 else 720
        val maxHeight = if (landscape) 720 else 1280
        val scale = minOf(1.0, minOf(maxWidth.toDouble() / sourceWidth, maxHeight.toDouble() / sourceHeight))
        return CaptureResolution(
            alignDown((sourceWidth * scale).toInt(), widthAlignment),
            alignDown((sourceHeight * scale).toInt(), heightAlignment),
        )
    }

    private fun alignDown(value: Int, alignment: Int): Int = (value / alignment) * alignment
}

object RecordingFilename {
    private const val PREFIX = "ScreenRecording_"
    fun fromEpochMillis(epochMillis: Long, collisionSequence: Long): String =
        "$PREFIX${epochMillis.coerceAtLeast(0)}_${collisionSequence.coerceAtLeast(0)}.mp4"
}

data class OutputCompletion(val hasVideoTrack: Boolean, val writtenVideoSamples: Int, val reachedEndOfStream: Boolean)

fun OutputCompletion.canPublish(): Boolean = hasVideoTrack && writtenVideoSamples > 0 && reachedEndOfStream

object Milestone5Settings {
    val default = RecordingSettings(
        RecordingPreset.Balanced,
        AudioMode.None,
        CaptureResolution(1280, 720),
        FrameRate(VideoConfigurationSelector.DEFAULT_FRAMES_PER_SECOND),
        VideoBitrate(VideoConfigurationSelector.DEFAULT_BITS_PER_SECOND),
        CountdownConfiguration.None,
        MaximumDurationPolicy.Unlimited,
    )
}
