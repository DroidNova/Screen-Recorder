package com.droidnova.screenrecorder.service

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.droidnova.screenrecorder.utils.PreferenceUtil

class ScreenRecordingService : Service() {

    var callback: RecordingCallbackInterface? = null
    private lateinit var serviceHelper: ServiceHelper
    var isServiceRunning = false
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        serviceHelper = ServiceHelper(this)
        PreferenceUtil.init(this)
        serviceHelper.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_STOP_SERVICE -> {
                    serviceHelper.stopScreenRecording()
                }
                ACTION_START_SERVICE -> {
                    val resultCode = it.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                    val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getParcelableExtra("data", Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getParcelableExtra("data")
                    }

                    if (data != null) {
                        serviceHelper.startForegroundServiceWithNotification(resultCode, data)
                    } else {
                        Log.e(TAG, "Failed to retrieve MediaProjection data")
                    }
                }
                ACTION_RESUME -> {
                    serviceHelper.resumeScreenRecording()
                }
                ACTION_PAUSE -> {
                    serviceHelper.pauseScreenRecording()
                }
                ACTION_STOP -> {
                    serviceHelper.stopScreenRecording()
                }
                else -> Log.w(TAG, "Unexpected action: ${it.action}")
            }
        }
        return START_STICKY
    }

    fun registerCallback(callback: RecordingCallbackInterface) {
        this.callback = callback
    }

    fun unregisterCallback() {
        this.callback = null
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordingService = this@ScreenRecordingService
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceHelper.stopScreenRecording()
    }

    interface RecordingCallbackInterface {
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun onRecordingTimeUpdate(timeString: String)     }

    companion object {
        const val TAG = "ScreenRecordingService"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_START_SERVICE = "action_start_service"
        const val ACTION_STOP = "com.droidnova.screenrecorder.STOP"
        const val ACTION_PAUSE = "com.droidnova.screenrecorder.PAUSE"
        const val ACTION_RESUME = "com.droidnova.screenrecorder.RESUME"
        const val FOREGROUND_SERVICE_ID = 101
    }
}
