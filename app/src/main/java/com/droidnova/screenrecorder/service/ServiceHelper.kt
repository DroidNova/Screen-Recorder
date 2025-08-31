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
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
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
import android.os.CountDownTimer
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.droidnova.screenrecorder.MainActivity
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.utils.EncodingUtil
import com.droidnova.screenrecorder.utils.FileUtil
import com.droidnova.screenrecorder.utils.MediaSettingsUtil
import com.droidnova.screenrecorder.utils.PreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ServiceHelper(private val service: ScreenRecordingService) {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var mediaRecorder: MediaRecorder
    private var windowManager: WindowManager? = null
    private lateinit var surface: Surface
    private var audioRecord: AudioRecord? = null
    private var videoPath: String? = null
    private var audioPath: String? = null

    private var recordingStartTime: Long = 0
    private var pausedTime = 0L
    private var totalElapsedTime = 0L
    private var isPaused = false

    fun onCreate() {
        createNotificationChannel()
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        // Display countdown on screen
        showCountdownOverlay {
            // After countdown completes, start the screen recording
            startScreenRecording(resultCode, data)
        }
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
        //setupAudioCapture()

        try {
            mediaRecorder.start()
            service.callback?.onRecordingStarted()
            startRecordingTimer()
        } catch (e: Exception) {
            Log.e(ScreenRecordingService.TAG, "Failed to start MediaRecorder", e)
            stopScreenRecording() // Ensure cleanup on failure
        }
    }

    fun pauseScreenRecording() {
        if (!isPaused) {
            mediaRecorder.pause()
            isPaused = true
            pausedTime = System.currentTimeMillis()
            totalElapsedTime += (pausedTime - recordingStartTime) // Accumulate the elapsed time
            updateNotification()
        }
    }

    fun resumeScreenRecording() {
        if (isPaused) {
            mediaRecorder.resume()
            isPaused = false
            recordingStartTime = System.currentTimeMillis() // Reset start time after resuming
            startRecordingTimer() // Start timer again
            updateNotification()
        }
    }

    private fun startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis()
        CoroutineScope(Dispatchers.Main).launch {
            while (service.isServiceRunning && !isPaused) {
                val elapsedTime = totalElapsedTime + (System.currentTimeMillis() - recordingStartTime)
                updateTimeUI(elapsedTime)
                delay(1000) // Update every second
            }
        }
    }

    private fun updateTimeUI(elapsedTime: Long) {
        val minutes = (elapsedTime / 1000) / 60
        val seconds = (elapsedTime / 1000) % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)

        // Send the updated time to the UI using the callback
        service.callback?.onRecordingTimeUpdate(timeString)
    }

    private fun setupMediaRecorder() {
        if (ActivityCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(ScreenRecordingService.TAG, "RECORD_AUDIO permission not granted")
            stopScreenRecording()
            return
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(service.applicationContext)
        } else {
            MediaRecorder()
        }.apply {
            // Fetch stored preferences using PreferenceUtil
            val selectedQuality = PreferenceUtil.selectedVideoQuality
            val selectedResolution = PreferenceUtil.selectedVideoResolution
            val selectedFps = PreferenceUtil.selectedVideoFps
            Log.e("myTag","selectedQuality $selectedQuality, selectedResolution $selectedResolution, selectedFps $selectedFps")

            // Map preferences to actual MediaRecorder settings
            val bitrate = MediaSettingsUtil.getBitRateFromQuality(selectedQuality)
            val displayMetrics = Resources.getSystem().displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            Log.e("myTag before ","screenWidth $screenWidth, screenHeight $screenHeight")

            val (videoWidth, videoHeight) = MediaSettingsUtil.getAdjustedResolution(selectedResolution, screenWidth, screenHeight)
            Log.e("myTag after","screenWidth $videoWidth, screenHeight $videoHeight")

            val fps = MediaSettingsUtil.getFpsFromString(selectedFps)

            // Set audio source to microphone
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // Set Video source to record
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            // Set output format
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            // Set video encoder
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            // Set audio encoder
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            // Set the mapped values for video size, bitrate, and FPS
            setVideoSize(videoWidth, videoHeight)
            setVideoEncodingBitRate(bitrate)
            setVideoFrameRate(fps)

            videoPath = FileUtil.createVideoFile(service, "ScreenRecording").absolutePath
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


    private fun setupVirtualDisplay() {
        val displayMetrics = Resources.getSystem().displayMetrics
        val selectedResolution = PreferenceUtil.selectedVideoResolution
        val (videoWidth, videoHeight) = MediaSettingsUtil.getAdjustedResolution(
            selectedResolution,
            displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )

        Log.e(ScreenRecordingService.TAG, "VirtualDisplay width: $videoWidth, height: $videoHeight")

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
        recordingStartTime = 0
        service.callback?.onRecordingTimeUpdate("00:00")
        stopForegroundService()
    }

    // Function to show countdown overlay
    private fun showCountdownOverlay(onCountdownComplete: () -> Unit) {
        val countdownText = TextView(service).apply {
            textSize = 60f
            setTextColor(Color.GREEN)
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent background
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Add countdownText to a full-screen overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(countdownText, params)

        // Initialize the countdown timer
        val countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = (millisUntilFinished / 1000 + 1).toString()
            }

            override fun onFinish() {
                // Remove the countdown overlay and start recording
                windowManager?.removeView(countdownText)
                onCountdownComplete() // Start recording
            }
        }
        countdownTimer.start()
    }


    private fun stopForegroundService() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        service.stopSelf()
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

    private fun updateNotification() {
        val notification = createNotification()
        // Get the system NotificationManager service
        val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Update the notification
        notificationManager.notify(ScreenRecordingService.FOREGROUND_SERVICE_ID, notification)
    }


    private fun createNotification(): Notification {
        // Intent for stopping the recording
        val stopIntent = Intent(service, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(service, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        // Intent for pausing the recording
        val pauseIntent = Intent(service, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(service, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        // Intent for resuming the recording
        val resumeIntent = Intent(service, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getService(service, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE)

        // Open app intent (MainActivity)
        val openHomeIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Notification builder
        val builder = NotificationCompat.Builder(service, ScreenRecordingService.FOREGROUND_SERVICE_ID.toString())
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)


        // Conditionally show either Pause or Resume based on current state
        if (isPaused) {
            builder.addAction(R.drawable.ic_delete, "Resume", resumePendingIntent) // Resume button
        } else {
            builder.addAction(R.drawable.ic_delete, "Pause", pausePendingIntent) // Pause button
        }
        builder.addAction(R.drawable.ic_delete, "Save", stopPendingIntent) // Stop button

        return builder.build()
    }


}
