package com.droidnova.screenrecorder.service

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ScreenRecordingService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var audioRecord: AudioRecord
    private lateinit var surface: Surface
    private var serviceHelper = ServiceHelper(this)
    private val binder = LocalBinder()
    private var callback: RecordingCallbackInterface? = null
    var isRecording = false

    private var videoPath: String? = null
    private var pcmAudioPath: String? = null



    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "onCreate")
        serviceHelper.createNotificationChannel()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_STOP_SERVICE ->{
                    Log.e("myTag mux", "ACTION_STOP_SERVICE")
                    stopScreenRecording()
                }

                ACTION_START_SERVICE -> {
                    val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)

                    val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Use the new type-safe method for Android 13+
                        it.getParcelableExtra("data", Intent::class.java)
                    } else {
                        // Use the deprecated method for older versions
                        @Suppress("DEPRECATION")
                        it.getParcelableExtra("data")
                    }

                    if (data != null) {
                        startForegroundServiceWithNotification(resultCode, data)
                    } else {
                        Log.e(TAG, "Failed to retrieve MediaProjection data")
                    }
                }
                else -> Log.w(TAG, "Unexpected action: ${it.action}")
            }
        }
        return START_STICKY
    }


    private fun startForegroundServiceWithNotification(resultCode: Int, data: Intent?) {
        Log.e(TAG, "startForegroundServiceWithNotification")
        val notification = serviceHelper.createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                FOREGROUND_SERVICE_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }
        Log.e(TAG, "startForegroundServiceWithNotification")
        isRecording = true
        startScreenRecording(resultCode, data)
    }

    private fun startScreenRecording(resultCode: Int, data: Intent?) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
        mediaProjection.registerCallback(MediaProjectionCallback(), null)

        setupVirtualDisplay()
        setupMediaRecorder()
        //startAudioCapture()
        try {
            mediaRecorder.start()
            callback?.onRecordingStarted()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Failed to start MediaRecorder", e)
        }
    }

    private fun setupMediaRecorder() {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)  // Use constructor with Context for API 31 and above
        } else {
            MediaRecorder()  // Use default constructor for API 30 and below
        }.apply {
            // Set the audio source first before setting the audio encoder
            setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)  // Use system audio

            // Set the video source
            setVideoSource(MediaRecorder.VideoSource.SURFACE)

            // Set the output format after setting both audio and video sources
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // Now you can set the encoders
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            // Set other properties like video size, frame rate, etc.
            setVideoSize(1920, 1080)
            setVideoFrameRate(30)

            videoPath = serviceHelper.createVideoFile().absolutePath
            setOutputFile(videoPath)

            try {
                prepare()  // Prepare the recorder
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Failed to prepare MediaRecorder", e)
                stopSelf()
            }


    }


        surface = mediaRecorder.surface
    }

    private fun setupVirtualDisplay() {
        val displayMetrics = getDisplayMetrics()

        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecording",
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            stopService()
        }
    }

//    private fun startAudioCapture() {
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.RECORD_AUDIO
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            // Handle permission denial
//            Log.e(TAG, "Audio recording permission not granted")
//            return
//        }
//
//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                // For Android 10 (API 29) and higher - use AudioPlaybackCapture to capture system audio
//                val audioPlaybackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
//                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
//                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
//                    .build()
//
//                val audioFormat = AudioFormat.Builder()
//                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
//                    .setSampleRate(44100)
//                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
//                    .build()
//
//                audioRecord = AudioRecord.Builder()
//                    .setAudioFormat(audioFormat)
//                    .setBufferSizeInBytes(8192)
//                    .setAudioPlaybackCaptureConfig(audioPlaybackConfig)
//                    .build()
//                Log.e("myTag audio",audioRecord.toString())
//            } else {
//                Log.e("myTag", "Audio recording permission not granted")
//                audioRecord = AudioRecord(
//                    MediaRecorder.AudioSource.MIC,   // Fallback to microphone input
//                    44100,                           // Sample rate
//                    AudioFormat.CHANNEL_IN_MONO,     // Mono channel
//                    AudioFormat.ENCODING_PCM_16BIT,  // 16-bit PCM encoding
//                    BUFFER_SIZE                      // Buffer size
//                )
//            }
//
//            pcmAudioPath = serviceHelper.getAudioFilePath("pcm")
//            audioRecord.startRecording()
//            pcmAudioPath?.let { saveAudioToFile(it) }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Log.e(TAG, "Failed to start AudioRecord", e)
//            stopSelf()
//        }
//    }

//    private fun saveAudioToFile(audioFilePath: String) {
//        val buffer = ByteArray(8192) // 8 KB buffer
//
//        GlobalScope.launch(Dispatchers.IO) {  // Run this in the background thread
//            try {
//                val outputStream = FileOutputStream(audioFilePath)
//                Log.e(TAG, "Writing PCM audio data to $audioFilePath")
//
//                while (isRecording) {
//                    Log.e("myTag audio", "Waiting for audio data...")
//
//                    // Read audio data from the AudioRecord buffer
//                    val read = audioRecord.read(buffer, 0, buffer.size)
//
//                    if (read > 0) {
//                        Log.e(TAG, "Writing $read bytes to file")
//                        outputStream.write(buffer, 0, read)  // Write the buffer content to the file
//                    } else if (read == 0) {
//                        Log.e(TAG, "No audio data read (read returned 0 bytes)")
//                    } else if (read < 0) {
//                        Log.e(TAG, "Error reading audio data: $read")  // Capture any error
//                        break
//                    }
//                }
//
//                outputStream.close()
//                audioRecord.stop()
//                audioRecord.release()
//
//                // Check if the file was written
//                val audioFile = File(audioFilePath)
//                if (audioFile.exists()) {
//                    Log.e(TAG, "PCM file size: ${audioFile.length()} bytes")
//                } else {
//                    Log.e(TAG, "PCM file not found after recording")
//                }
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                Log.e(TAG, "Error while saving PCM audio data: ${e.message}")
//            }
//        }
//    }




    private fun getDisplayMetrics(): DisplayMetrics {
        val displayMetrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics
    }

    private fun stopScreenRecording() {
        Log.e("myTag mux", "stopRecording")
        if (!isRecording) return
        isRecording = false
        val outputFilePath = serviceHelper.createVideoFile().absolutePath

        try {
            if (::mediaRecorder.isInitialized) {
                try {
                    mediaRecorder.stop()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "MediaRecorder stop failed: ${e.message}")
                }
                mediaRecorder.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Failed to release MediaRecorder", e)
        }

        if (::audioRecord.isInitialized) {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord.release()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Failed to stop and release AudioRecord", e)
            }
        }

        virtualDisplay.release()
        mediaProjection.stop()
        callback?.onRecordingStopped()
//        Log.e("myTag mux", "Screen recording stopped")
//        // Mux audio and video
//        val aacFilePath = serviceHelper.getAudioFilePath("aac")
//        val audioFile = pcmAudioPath?.let { File(it) }
//        if (audioFile?.exists() == true) {
//            Log.e(TAG, "PCM file size: ${audioFile.length()} bytes")
//        } else {
//            Log.e(TAG, "PCM file not found after recording")
//        }
//        Log.e("myTag mux", "aacFilePath: $aacFilePath")
//        CoroutineScope(Dispatchers.Main).launch {
//            pcmAudioPath?.let { serviceHelper.encodePcmToAac(it,aacFilePath) }
//            videoPath?.let { serviceHelper.muxAudioAndVideo(it, aacFilePath, outputFilePath) }
//            stopService()
//        }
        stopService()


    }


    private fun stopService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Handle MediaProjection token revocation
    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            stopScreenRecording()
        }
    }

    fun registerCallback(callback: RecordingCallbackInterface) {
        this.callback = callback
    }

    fun unregisterCallback() {
        this.callback = null
    }

    override fun onBind(intent: Intent?): IBinder{
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordingService = this@ScreenRecordingService
    }

    interface RecordingCallbackInterface {
        fun onRecordingStarted()
        fun onRecordingStopped()
    }

    companion object {
        const val TAG = "myTag"
        const val FOREGROUND_CHANNEL_ID = "foreground_channel_id"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_START_SERVICE = "action_start_service"
        const val FOREGROUND_SERVICE_ID = 101
        const val BUFFER_SIZE = 1024 * 1024 // 1 MB buffer size
    }
}
