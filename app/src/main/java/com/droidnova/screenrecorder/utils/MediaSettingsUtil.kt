package com.droidnova.screenrecorder.utils

object MediaSettingsUtil {

    // Convert quality string to bitrate
    fun getBitRateFromQuality(quality: String): Int {
        return when (quality) {
            "3Mbps" -> 3_000_000
            "6Mbps" -> 6_000_000
            "8Mbps" -> 8_000_000
            "10Mbps" -> 10_000_000
            "12Mbps" -> 12_000_000
            "15Mbps" -> 15_000_000
            else -> 10_000_000 // Default to 10Mbps if unknown
        }
    }

    // Convert resolution string to width and height
    fun getResolutionFromString(resolution: String): Pair<Int, Int> {
        return when (resolution) {
            "360p" -> Pair(480, 360)
            "480p" -> Pair(854, 480)
            "540p" -> Pair(960, 540)
            "640p" -> Pair(640, 360)
            "720p" -> Pair(1280, 720)
            "1080p" -> Pair(1920, 1080)
            else -> Pair(1280, 720) // Default to 720p if unknown
        }
    }

    // Convert FPS string to integer
    fun getFpsFromString(fps: String): Int {
        return when (fps) {
            "30fps" -> 30
            "60fps" -> 60
            else -> 30 // Default to 30fps if unknown
        }
    }
}
