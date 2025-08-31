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
    fun getAdjustedResolution(resolution: String, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        // Base resolutions are defined in landscape orientation (width x height)
        val (baseWidth, baseHeight) = when (resolution) {
            "360p" -> Pair(640, 360)
            "480p" -> Pair(854, 480)
            "720p" -> Pair(1280, 720)
            "1080p" -> Pair(1920, 1080)
            else -> Pair(1280, 720)
        }

        val screenAspectRatio = screenWidth.toFloat() / screenHeight

        return if (screenAspectRatio >= 1f) {
            // Landscape orientation – keep the base height and scale width to match
            val height = baseHeight
            val width = (height * screenAspectRatio).toInt()
            Pair(width, height)
        } else {
            // Portrait orientation – keep the base height as the width and scale height
            val width = baseHeight
            val height = (width / screenAspectRatio).toInt()
            Pair(width, height)
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
