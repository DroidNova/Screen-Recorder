package com.droidnova.screenrecorder.utils

import android.content.Context
import android.os.Environment
import java.io.File

object FileUtil {
    fun createVideoFile(context: Context,fileName: String): File {
        val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(videosDir, "$fileName${System.currentTimeMillis()}.mp4")
    }

    fun createAudioFile(context: Context): File {
        val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(videosDir, "audio_${System.currentTimeMillis()}.aac")
    }
}
