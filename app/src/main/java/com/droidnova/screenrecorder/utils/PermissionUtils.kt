package com.droidnova.screenrecorder.utils

import android.content.Context
import androidx.core.net.toUri

object PermissionUtils {
    fun isFolderAccessGranted(context: Context, folderPath: String?): Boolean {
        if (folderPath != null) {
            val uri = folderPath.toUri()
            val uriPermissions = context.contentResolver.persistedUriPermissions
            return uriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        } else {
            return false
        }
    }
}