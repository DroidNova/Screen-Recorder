package com.droidnova.screenrecorder

import android.app.Application
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.droidnova.screenrecorder.ui.screens.recordingsscreen.VideoModel
import com.droidnova.screenrecorder.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var fileObserver: FileObserver? = null
    private var watchService: WatchService? = null

    private val _allVideos = MutableLiveData<List<VideoModel>>()
    val allVideos: LiveData<List<VideoModel>> = _allVideos

    private val _recordingEvents: MutableSharedFlow<RecordingEvent> = MutableSharedFlow()
    val recordingEvents: SharedFlow<RecordingEvent> = _recordingEvents

    init {
        Log.e("myTag", "MainViewModel init")
        loadVideosFromFolder()
    }


    fun setupFileObserver() {
        val context = getApplication<Application>()
        val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)

        if (videosDir != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                setupFileObserverLegacy(videosDir)
            } else {
                setupWatchService(videosDir)
            }
        } else {
            Log.e("myTag", "Videos directory not found.")
        }
    }

    private fun setupFileObserverLegacy(videosDir: File) {
        fileObserver = object : FileObserver(
            videosDir.path,
            FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY
        ) {
            override fun onEvent(event: Int, path: String?) {
                // Use viewModelScope to safely perform actions
                viewModelScope.launch(Dispatchers.IO) {
                    loadVideosFromFolder()
                }
            }
        }
        fileObserver?.startWatching()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupWatchService(videosDir: File) {
        val path = videosDir.toPath()
        viewModelScope.launch {
            try {
                watchService = FileSystems.getDefault().newWatchService()
                path.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )

                withContext(Dispatchers.IO) {
                    while (true) {
                        val key = watchService?.take() ?: break
                        for (event in key.pollEvents()) {
                            when (event.kind()) {
                                StandardWatchEventKinds.ENTRY_CREATE -> {
                                    Log.d("myTag", "File created: ${event.context()}")
                                }
                                StandardWatchEventKinds.ENTRY_DELETE -> {
                                    Log.d("myTag", "File deleted: ${event.context()}")
                                }
                                StandardWatchEventKinds.ENTRY_MODIFY -> {
                                    Log.d("myTag", "File modified: ${event.context()}")
                                }
                            }
                            // Refresh the list of videos
                            loadVideosFromFolder()
                        }
                        key.reset() // Reset the key to receive further events
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                watchService?.close()
            }
        }
    }

    fun loadVideosFromFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)

            if (videosDir != null) {
                val videoFiles = videosDir.listFiles { file ->
//                    Log.e("myTag", "File: ${file.name}")
                    file.isFile && (file.extension.equals("mp4", ignoreCase = true) ||
                            file.extension.equals("wav", ignoreCase = true) ||
                            file.extension.equals("aac", ignoreCase = true))
                }?.map {
                    val fileSize = VideoUtils.getFormattedFileSize(it.length())
                    val duration = VideoUtils.getVideoDurationInFormat(it)
                    val lastModified = it.lastModified()
                    VideoModel(it.absolutePath,it.name, fileSize, duration, lastModified)
                }?.toList()?.sortedByDescending { it.lastModified } ?: emptyList()

                Log.e("myTag", "Video files: ${videoFiles.size}")
                _allVideos.postValue(videoFiles)
            } else {
                Log.e("myTag", "Videos directory not found.")
            }
        }
    }

    fun deleteVideo(videoFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (videoFile.exists()) {
                val fileDeleted = videoFile.delete()
                Log.d("myTag", "File deleted: $fileDeleted")

                if (fileDeleted) {
                    // Update the video list
                    loadVideosFromFolder()
                    sendRecordingEvent(RecordingEvent.ShowDeleteResultToast(true))
                } else {
                    // Handle file deletion failure
                    sendRecordingEvent(RecordingEvent.ShowDeleteResultToast(false))
                }
            } else {
                Log.e("myTag", "File does not exist: ${videoFile.absolutePath}")
                sendRecordingEvent(RecordingEvent.ShowDeleteResultToast(false))
            }
        }
    }

    fun renameVideo(videoFile: File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure the new name has the .mp4 extension
            val newFileName = if (newName.endsWith(".mp4", ignoreCase = true)) {
                newName
            } else {
                "$newName.mp4"
            }
            val newFile = File(videoFile.parent, newFileName)
            val renamed = videoFile.renameTo(newFile)
            withContext(Dispatchers.Main) {
                Log.d("myTag", "Renaming video: ${videoFile.name} to $newFileName - Success: $renamed")
                if (renamed) {
                    // Reload videos or update UI as needed
                    loadVideosFromFolder()
                } else {
                    Log.e("myTag", "File renaming failed: ${videoFile.absolutePath}")
                }
            }
        }
    }

    private fun sendRecordingEvent(event: RecordingEvent) {
        viewModelScope.launch {
            _recordingEvents.emit(event)
        }
    }

    sealed interface RecordingEvent {
        data class ShowDeleteResultToast(val deleted: Boolean) : RecordingEvent
    }

    fun removeObserver(){
        fileObserver?.stopWatching()
        fileObserver =null
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
    }

    fun deleteSelectedVideos(selectedItems: Set<VideoModel>, progressDialog: AlertDialog, onMessageUpdate: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Perform the deletion
            val context = getApplication<Application>()
            val videosDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)

            if (videosDir != null) {
                selectedItems.forEach { video ->
                    val file = File(video.filePath)
                    if (file.exists()) {
                        // Update the message to indicate progress
                        withContext(Dispatchers.Main) {
                            onMessageUpdate("Deleting ${video.fileName}...")
                        }
                        file.delete()
                    }
                }
            }

            // Notify the UI about the completion of deletion
            withContext(Dispatchers.Main) {
                loadVideosFromFolder()
                sendRecordingEvent(RecordingEvent.ShowDeleteResultToast(true))
                // Dismiss progress dialog
                progressDialog.dismiss()
            }
        }
    }


}