package com.droidnova.screenrecorder.recording

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.droidnova.screenrecorder.domain.recording.CaptureResolution
import com.droidnova.screenrecorder.domain.recording.FrameRate
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.domain.recording.VideoBitrate
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingCapabilities
import com.droidnova.screenrecorder.domain.recording.VideoBitrateRange
import com.droidnova.screenrecorder.domain.recording.VideoCapabilityProfile

data class AvailableVideoConfiguration(
    val encoderName: String,
    val preset: RecordingPreset,
    val shortEdge: Int,
    val resolution: CaptureResolution,
    val frameRate: FrameRate,
    val bitrate: VideoBitrate,
)

object AvcCapabilityProvider {
    private val shortEdges = listOf(480, 720, 1080)
    private val frameRates = listOf(24, 30, 60)
    private val bitrates = listOf(2_000_000, 6_000_000, 12_000_000)

    fun query(sourceWidth: Int, sourceHeight: Int): List<AvailableVideoConfiguration> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return emptyList()
        val encoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.asSequence()
            .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .mapNotNull { info ->
                runCatching {
                    val capabilities = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    if (MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface !in capabilities.colorFormats) return@runCatching null
                    val video = capabilities.videoCapabilities ?: return@runCatching null
                    info to video
                }.getOrNull()
            }
            .sortedWith(compareByDescending<Pair<MediaCodecInfo, MediaCodecInfo.VideoCapabilities>> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.first.isHardwareAccelerated else !isSoftware(it.first.name)
            }.thenBy { it.first.name })
            .toList()
        return encoders.firstNotNullOfOrNull { (info, video) ->
            val configurations = buildList {
                shortEdges.forEach shortEdgeLoop@{ shortEdge ->
                    val resolution = resolutionFor(
                        sourceWidth,
                        sourceHeight,
                        shortEdge,
                        video.widthAlignment,
                        video.heightAlignment,
                    ) ?: return@shortEdgeLoop
                    if (!video.isSizeSupported(resolution.width, resolution.height)) return@shortEdgeLoop
                    frameRates.forEach fpsLoop@{ fps ->
                        if (!video.areSizeAndRateSupported(resolution.width, resolution.height, fps.toDouble())) return@fpsLoop
                        bitrates.map { ideal -> ideal to ideal.coerceIn(video.bitrateRange.lower, video.bitrateRange.upper) }
                            .filter { (_, actual) -> actual in 1_000_000..16_000_000 }
                            .distinctBy { it.second }
                            .forEach { (idealBitrate, bitrate) ->
                                add(
                                    AvailableVideoConfiguration(
                                        encoderName = info.name,
                                        preset = presetFor(shortEdge, fps, idealBitrate),
                                        shortEdge = shortEdge,
                                        resolution = resolution,
                                        frameRate = FrameRate(fps),
                                        bitrate = VideoBitrate(bitrate),
                                    ),
                                )
                            }
                    }
                }
            }
            configurations.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    fun domainCapabilities(configurations: List<AvailableVideoConfiguration>): RecordingCapabilities? {
        if (configurations.isEmpty()) return null
        return RecordingCapabilities(
            videoProfiles = configurations.map {
                VideoCapabilityProfile(
                    resolution = it.resolution,
                    supportedFrameRates = setOf(it.frameRate),
                    bitrateRange = VideoBitrateRange(it.bitrate, it.bitrate),
                )
            },
            supportedAudioModes = setOf(AudioMode.None, AudioMode.Microphone, AudioMode.DeviceAudio),
            supportsPauseResume = false,
        )
    }

    private fun resolutionFor(
        sourceWidth: Int,
        sourceHeight: Int,
        shortEdge: Int,
        widthAlignment: Int,
        heightAlignment: Int,
    ): CaptureResolution? {
        val sourceShortEdge = minOf(sourceWidth, sourceHeight)
        val minimumSourceShortEdge = if (shortEdge == 1080) 1_024 else shortEdge
        if (sourceShortEdge < minimumSourceShortEdge) return null
        val resolvedShortEdge = minOf(shortEdge, sourceShortEdge)
        val scale = resolvedShortEdge.toDouble() / sourceShortEdge
        val width = alignDown((sourceWidth * scale).toInt(), widthAlignment)
        val height = alignDown((sourceHeight * scale).toInt(), heightAlignment)
        return if (width > 0 && height > 0) CaptureResolution(width, height) else null
    }

    private fun presetFor(shortEdge: Int, fps: Int, bitrate: Int): RecordingPreset = when {
        shortEdge == 480 && fps <= 30 && bitrate == 2_000_000 -> RecordingPreset.DataSaver
        shortEdge == 720 && fps == 30 && bitrate == 6_000_000 -> RecordingPreset.Balanced
        shortEdge == 1080 && fps in setOf(30, 60) && bitrate == 12_000_000 -> RecordingPreset.HighQuality
        else -> RecordingPreset.Custom
    }

    private fun alignDown(value: Int, alignment: Int): Int = value / alignment * alignment
    private fun isSoftware(name: String): Boolean = name.startsWith("OMX.google.", true) || name.startsWith("c2.android.", true)
}
