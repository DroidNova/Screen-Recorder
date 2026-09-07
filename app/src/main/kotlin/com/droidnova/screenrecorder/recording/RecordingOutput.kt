package com.droidnova.screenrecorder.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

internal class RecordingOutput private constructor(
    private val context: Context,
    private val pendingUri: Uri?,
    private var descriptor: ParcelFileDescriptor?,
    private val legacyFile: File?,
) {
    fun createMuxer(): MediaMuxer = if (pendingUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        MediaMuxer(requireNotNull(descriptor).fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    } else {
        MediaMuxer(requireNotNull(legacyFile).absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun publish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            check(context.contentResolver.update(requireNotNull(pendingUri), values, null, null) == 1)
        }
    }

    fun discard() {
        pendingUri?.let { context.contentResolver.delete(it, null, null) }
        legacyFile?.delete()
    }

    fun closeDescriptor() {
        descriptor?.close()
        descriptor = null
    }

    companion object {
        fun create(context: Context, epochMillis: Long, collisionSequence: Long): RecordingOutput {
            val displayName = RecordingFilename.fromEpochMillis(epochMillis, collisionSequence)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Screen Recorder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = requireNotNull(
                    context.contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values),
                )
                val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                if (descriptor == null) {
                    context.contentResolver.delete(uri, null, null)
                    error("output descriptor unavailable")
                }
                return RecordingOutput(context, uri, descriptor, null)
            }
            val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES))
            return RecordingOutput(context, null, null, File(directory, displayName))
        }
    }
}
