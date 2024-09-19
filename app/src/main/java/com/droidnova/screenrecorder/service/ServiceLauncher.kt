package com.droidnova.screenrecorder.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object ServiceLauncher {

    fun startScreenRecordingService(context: Context,resultCode: Int, data: Intent) {
        val serviceIntent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START_SERVICE
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        Log.e(ScreenRecordingService.TAG,"startScreenRecordingService")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }


    fun stopService(context: Context) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
    }
}
