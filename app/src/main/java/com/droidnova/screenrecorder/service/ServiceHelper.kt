package com.droidnova.screenrecorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.PermissionChecker.checkSelfPermission
import com.droidnova.screenrecorder.MainActivity
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.utils.EncodingUtil
import com.droidnova.screenrecorder.utils.FileUtil
import com.droidnova.screenrecorder.utils.MuxingUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ServiceHelper(private val service: ScreenRecordingService) {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var surface: Surface
    private var audioRecord: AudioRecord? = null
    private var videoPath: String? = null
    private var audioPath: String? = null

    fun onCreate() {
        createNotificationChannel()
        mediaProjectionManager = service.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // Notification channel setup for Android O+
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(ScreenRecordingService.FOREGROUND_SERVICE_ID.toString())

            if (channel == null) {
                val name = service.getString(R.string.screen_recording_channel_name)
                val description = service.getString(R.string.screen_recording_channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT

                NotificationChannel(ScreenRecordingService.FOREGROUND_SERVICE_ID.toString(), name, importance).apply {
                    this.description = description
                    notificationManager.createNotificationChannel(this)
                }
            }
        }
    }

    // Starts the foreground service with a notification and begins screen recording
    fun startForegroundServiceWithNotification(resultCode: Int, data: Intent) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.startForeground(
                ScreenRecordingService.FOREGROUND_SERVICE_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            service.startForeground(ScreenRecordingService.FOREGROUND_SERVICE_ID, notification)
        }

        service.isServiceRunning = true
        startScreenRecording(resultCode, data)
    }

    private fun startScreenRecording(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopScreenRecording()
            }
        }, null)

        setupMediaRecorder()
        setupVirtualDisplay()
        setupAudioCapture()

        try {
            mediaRecorder.start()
            service.callback?.onRecordingStarted()
        } catch (e: Exception) {
            Log.e(ScreenRecordingService.TAG, "Failed to start MediaRecorder", e)
            stopScreenRecording() // Ensure cleanup on failure
        }
    }

    private fun setupMediaRecorder() {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(service)
        } else {
            MediaRecorder()
        }.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            val displayMetrics = getDisplayMetrics()
            setVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
            setVideoEncodingBitRate(15_000_000)
            setVideoFrameRate(60)

            videoPath = FileUtil.createVideoFile(service, "Temp").absolutePath
            setOutputFile(videoPath)

            try {
                prepare()
            } catch (e: Exception) {
                Log.e(ScreenRecordingService.TAG, "Failed to prepare MediaRecorder", e)
                stopScreenRecording()
            }
        }
        surface = mediaRecorder.surface
    }

    private fun setupAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val audioPlaybackCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            if (ActivityCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfig)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioRecord?.startRecording()

            audioPath = FileUtil.createAudioFile(service).absolutePath
            audioRecord?.let { record ->
                audioPath?.let { path ->
                    EncodingUtil.startRealTimeEncoding(record, path)
                }
            }
        } else {
            Log.e(ScreenRecordingService.TAG, "System audio capture is not supported below Android 10")
        }
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
            Log.e(ScreenRecordingService.TAG, "Failed to create VirtualDisplay", e)
            stopScreenRecording()
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val displayMetrics = DisplayMetrics()
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics
    }

    fun stopScreenRecording() {
        if (!service.isServiceRunning) return
        service.isServiceRunning = false

        try {
            mediaRecorder.stop()
            mediaRecorder.release()
            audioRecord?.stop()
            audioRecord?.release()
            Log.i(ScreenRecordingService.TAG, "MediaRecorder stopped")
        } catch (e: IllegalStateException) {
            Log.e(ScreenRecordingService.TAG, "MediaRecorder stop failed: ${e.message}")
        }

        virtualDisplay.release()
        mediaProjection.stop()
        service.callback?.onRecordingStopped()

        CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            try {
                if (videoPath != null && audioPath != null) {
                    MuxingUtil.muxAudioAndVideo(videoPath!!, audioPath!!, FileUtil.createVideoFile(service,"ScreenRecording").absolutePath)
                    Log.i(ScreenRecordingService.TAG, "Muxing completed")
                } else {
                    Log.e(ScreenRecordingService.TAG, "Muxing failed: videoPath or audioPath is null")
                }
                withContext(Dispatchers.Main) {
                    stopForegroundService()
                }
            } catch (e: Exception) {
                Log.e(ScreenRecordingService.TAG, "Muxing failed: ${e.message}")
            }
        }
    }

    private fun stopForegroundService() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        service.stopSelf()
    }

    private fun createNotification(): Notification {
        val openHomeIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val openHomePendingIntent = PendingIntent.getActivity(service, 0, openHomeIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(service, ScreenRecordingService.FOREGROUND_SERVICE_ID.toString())
            .setContentTitle(service.getString(R.string.screen_recording_title))
            .setContentText(service.getString(R.string.screen_recording_text))
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openHomePendingIntent)
            .build()
    }
}
