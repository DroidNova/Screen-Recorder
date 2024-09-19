package com.droidnova.screenrecorder.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoUtils {

    fun getVideoDurationInFormat(file: File): String {
        // Get duration in milliseconds
        val durationMillis = retrieveVideoDuration(file)

        // Convert milliseconds to seconds
        val elapsedSeconds = (durationMillis / 1000).toInt()

        // Format the duration
        return convertDurationInFormat(elapsedSeconds)
    }

    fun getCreationDate(lastModified: Long): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(lastModified))
    }


    fun getFormattedFileSize(sizeInBytes: Long): String {
        val sizeInGb = sizeInBytes / (1024.0 * 1024.0 * 1024.0)
        val sizeInMb = sizeInBytes / (1024.0 * 1024.0)
        val formatter = DecimalFormat("#.##")
        return if (sizeInGb >= 1) {
            formatter.format(sizeInGb) + " GB"
        } else {
            formatter.format(sizeInMb) + " MB"
        }
    }

    fun getStorageInfo(context: Context): Pair<Long, Long> {
        val storageDirectory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(null)
        } else {
            Environment.getExternalStorageDirectory()
        }

        storageDirectory?.let {
            val stat = StatFs(it.path)

            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            val totalBlocks = stat.blockCountLong

            val availableSpace = availableBlocks * blockSize
            val totalSpace = totalBlocks * blockSize

            return Pair(availableSpace, totalSpace)
        }

        // Return 0 if storage directory is not available
        return Pair(0, 0)
    }


    fun getHumanReadablePath(context: Context, uri: Uri): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // For KitKat and above, use DocumentFile
            getPathFromDocumentUri(context, uri)
        } else {
            // For older versions, URI path handling may be different
            uri.path ?: "Unknown Path"
        }
    }

    private fun getPathFromDocumentUri(context: Context, uri: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val split = documentId.split(":")

        // If the split array has fewer than 2 elements, return the last part of the documentId as the folder name
        if (split.size < 2) {
            // Return the last part of the documentId as the folder name
            return documentId.substringAfterLast('/')
        }

        val rootId = split[0]
        val relativePath = split[1]

        // Map known root IDs to more user-friendly names
        val rootName = when (rootId) {
            "primary" -> "sdcard"
            else -> rootId
        }

        // Reconstruct the path
        val path = "$rootName/${relativePath.replaceFirst("document/", "")}"
        return path.replace("/", File.separator)
    }


    private fun retrieveVideoDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        } finally {
            retriever.release()
        }
    }

    private fun convertDurationInFormat(elapsedSeconds: Int): String {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

}