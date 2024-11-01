package com.droidnova.screenrecorder.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.droidnova.screenrecorder.ui.bottom_sheets.PermissionBottomSheetFragment

object ServiceLauncher {

    fun startScreenRecordingService(context: Context,fragment: Fragment, resultCode: Int, data: Intent? ) {
        val serviceIntent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START_SERVICE
            putExtra("resultCode", resultCode)
            putExtra("data", data)
        }
        Log.d(ScreenRecordingService.TAG, "Starting Screen Recording Service")

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

    fun areAllPermissionsGranted(context: Context): Boolean {
        val audioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val overlayPermission = Settings.canDrawOverlays(context)

        return audioPermission && notificationPermission && overlayPermission
    }

}
