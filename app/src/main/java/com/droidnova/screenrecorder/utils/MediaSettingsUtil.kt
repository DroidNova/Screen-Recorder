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
        // Using the device's full width and height prevents letterboxing in the
        // recorded video. The selected resolution parameter is retained for
        // future use but currently ignored.
        return Pair(screenWidth, screenHeight)
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
