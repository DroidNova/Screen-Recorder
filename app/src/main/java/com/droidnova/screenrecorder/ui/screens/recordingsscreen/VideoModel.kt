package com.droidnova.screenrecorder.ui.screens.recordingsscreen

import java.io.File

data class VideoModel(
    val filePath: String,
    val fileName: String,
    val fileSize: String,
    var videoDuration: String,
    var lastModified: Long
)
