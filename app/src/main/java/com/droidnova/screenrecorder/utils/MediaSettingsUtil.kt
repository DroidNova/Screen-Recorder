package com.droidnova.screenrecorder.utils

import android.util.Log

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
        val (baseHeight, baseWidth) = when (resolution) {
            "360p" -> Pair(640, 360)
            "480p" -> Pair(854, 480)
            "720p" -> Pair(1280, 720)
            "1080p" -> Pair(1920, 1080)
            else -> Pair(1280, 720)
        }

//        return Pair(baseWidth, baseHeight)

        // Calculate aspect ratios
        val screenAspectRatio = screenWidth.toFloat() / screenHeight
        val resolutionAspectRatio = baseWidth.toFloat() / baseHeight

        Log.e("myTag", "screenAspectRatio $screenAspectRatio, resolutionAspectRatio $resolutionAspectRatio")

        // Adjust the resolution to fit the screen aspect ratio
        return if (screenAspectRatio > resolutionAspectRatio) {
            // Scale based on height
            val adjustedWidth = (baseHeight * screenAspectRatio).toInt()
            Pair(adjustedWidth, baseHeight)
        } else {
            // Scale based on width
            val adjustedHeight = (baseWidth / screenAspectRatio).toInt()
            Pair(baseWidth, adjustedHeight)
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
