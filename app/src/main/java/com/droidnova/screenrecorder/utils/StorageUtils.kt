package com.droidnova.screenrecorder.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log

object StorageUtils {

    fun getStorageInfo(context: Context): Pair<Double, Double> {
        val storageDirectory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(null) // Scoped storage path for Android 10 and above
        } else {
            Environment.getExternalStorageDirectory() // Deprecated after Android 10
        }

        storageDirectory?.let {
            try {
                // Check if the directory exists
                if (it.exists()) {
                    val stat = StatFs(it.path)

                    val blockSize = stat.blockSizeLong
                    val availableBlocks = stat.freeBlocksLong
                    val totalBlocks = stat.blockCountLong

                    val availableSpaceBytes = availableBlocks * blockSize
                    val totalSpaceBytes = totalBlocks * blockSize

                    // Convert bytes to GB
                    val availableSpaceGB = availableSpaceBytes.toDouble() / (1024 * 1024 * 1024)
                    val totalSpaceGB = totalSpaceBytes.toDouble() / (1024 * 1024 * 1024)

                    // Return available and total space in GB, rounded to 2 decimal places
                    return Pair(
                        String.format("%.2f", availableSpaceGB).toDouble(),
                        String.format("%.2f", totalSpaceGB).toDouble()
                    )
                } else {
                    Log.e("getStorageInfo", "Storage directory does not exist: ${it.path}")
                }
            } catch (e: IllegalArgumentException) {
                Log.e("getStorageInfo", "Invalid storage directory path: ${e.message}")
            } catch (e: Exception) {
                Log.e("getStorageInfo", "Error retrieving storage info: ${e.message}")
            }
        }
        // Return 0 if storage directory is not available or an error occurred
        return Pair(0.0, 0.0)
    }


}
