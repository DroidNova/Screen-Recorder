package com.droidnova.screenrecorder.feature.recordings

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.droidnova.screenrecorder.recording.RecordingFilename
import com.droidnova.screenrecorder.recording.RecordingOutput

internal enum class RecordingOrigin { Current, Legacy }

internal data class LibraryRecording(
    val contentUri: Uri,
    val id: Long,
    val displayName: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
    val width: Long,
    val height: Long,
    val volumeName: String?,
    val origin: RecordingOrigin,
)

internal sealed interface LibraryResult {
    data class Success(val recordings: List<LibraryRecording>) : LibraryResult
    data object PermissionLimited : LibraryResult
    data object Error : LibraryResult
}

internal sealed interface ModificationResult {
    data object Success : ModificationResult
    data object Missing : ModificationResult
    data class ConsentRequired(val intentSender: android.content.IntentSender) : ModificationResult
    data object Failed : ModificationResult
}

internal class RecordingsRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun query(legacyPermissionGranted: Boolean): LibraryResult = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !legacyPermissionGranted) {
            LibraryResult.PermissionLimited
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) scanLegacyDirectoryOnce()
            val rows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) queryCurrent() else queryLegacyVolume()
            LibraryResult.Success(rows.distinctBy { it.contentUri.toString() }.sortedWith(
                compareByDescending<LibraryRecording> { it.modifiedSeconds }.thenByDescending { it.id },
            ))
        }
    } catch (_: Exception) {
        LibraryResult.Error
    }

    private fun scanLegacyDirectoryOnce() {
        val preferences = context.getSharedPreferences("recordings_library", Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_LEGACY_SCAN_COMPLETE, false)) return
        val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.canonicalFile ?: return
        val paths = directory.listFiles()?.asSequence()?.filter { file ->
            file.isFile && (RecordingFilename.isAppRecording(file.name) || LEGACY_NAME.matches(file.name)) &&
                !file.name.endsWith(".partial", ignoreCase = true)
        }?.map { it.absolutePath }?.toList().orEmpty()
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), Array(paths.size) { RecordingOutput.MIME_TYPE }, null)
        preferences.edit().putBoolean(KEY_LEGACY_SCAN_COMPLETE, true).commit()
    }

    private fun queryCurrent(): List<LibraryRecording> {
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection = MediaStore.Video.Media.getContentUri(volume)
        val selection = "${MediaStore.Video.Media.MIME_TYPE}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=? AND " +
            "${MediaStore.Video.Media.IS_PENDING}=0 AND ${MediaStore.Video.Media.SIZE}>0"
        return readCursor(collection, selection, arrayOf(RecordingOutput.MIME_TYPE, "${RecordingOutput.RELATIVE_PATH}/"), volume)
    }

    @Suppress("DEPRECATION")
    private fun queryLegacyVolume(): List<LibraryRecording> {
        val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)?.canonicalFile
            ?: return emptyList()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val rows = readCursor(
            collection,
            "${MediaStore.Video.Media.MIME_TYPE}=? AND ${MediaStore.Video.Media.DATA} LIKE ? AND " +
                "${MediaStore.Video.Media.DATA} NOT LIKE ? AND ${MediaStore.Video.Media.SIZE}>0",
            arrayOf(RecordingOutput.MIME_TYPE, "${directory.absolutePath}/%", "${directory.absolutePath}/%/%"),
            null,
        )
        return rows.filter {
            val name = it.displayName
            name.endsWith(".mp4", ignoreCase = true) && !name.endsWith(".partial", ignoreCase = true)
        }.map { it.copy(origin = if (LEGACY_NAME.matches(it.displayName)) RecordingOrigin.Legacy else RecordingOrigin.Current) }
    }

    private fun readCursor(collection: Uri, selection: String, args: Array<String>, volume: String?): List<LibraryRecording> {
        val projection = mutableListOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Video.Media.VOLUME_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Video.Media.OWNER_PACKAGE_NAME)
        }.toTypedArray()
        return resolver.query(collection, projection, selection, args,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC, ${MediaStore.Video.Media._ID} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val volumeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME) else -1
            val ownerIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.OWNER_PACKAGE_NAME) else -1
            buildList {
                while (cursor.moveToNext()) {
                    if (ownerIndex >= 0 && cursor.getString(ownerIndex) != context.packageName) continue
                    val id = cursor.getLong(idIndex)
                    val actualVolume = if (volumeIndex >= 0) cursor.getString(volumeIndex) else volume
                    val actualCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(actualVolume ?: MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else collection
                    add(LibraryRecording(ContentUris.withAppendedId(actualCollection, id), id, cursor.getString(nameIndex),
                        cursor.getLong(durationIndex).coerceAtLeast(0), cursor.getLong(sizeIndex), cursor.getLong(modifiedIndex),
                        cursor.getLong(widthIndex), cursor.getLong(heightIndex), actualVolume, RecordingOrigin.Current))
                }
            }
        } ?: emptyList()
    }

    fun rename(uri: Uri, displayName: String): ModificationResult = try {
        if (resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, displayName) }, null, null) > 0) {
            ModificationResult.Success
        } else ModificationResult.Missing
    } catch (error: RecoverableSecurityException) {
        ModificationResult.ConsentRequired(error.userAction.actionIntent.intentSender)
    } catch (_: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ModificationResult.ConsentRequired(MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender)
        } else ModificationResult.Failed
    } catch (_: Exception) {
        ModificationResult.Failed
    }

    fun delete(uri: Uri): ModificationResult = try {
        if (resolver.delete(uri, null, null) > 0) ModificationResult.Success else ModificationResult.Missing
    } catch (error: RecoverableSecurityException) {
        ModificationResult.ConsentRequired(error.userAction.actionIntent.intentSender)
    } catch (_: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ModificationResult.ConsentRequired(MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender)
        } else ModificationResult.Failed
    } catch (_: Exception) {
        ModificationResult.Failed
    }

    fun observe(onChanged: () -> Unit): ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = onChanged()
    }.also { resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, it) }

    fun stopObserving(observer: ContentObserver) = resolver.unregisterContentObserver(observer)

    companion object {
        private const val KEY_LEGACY_SCAN_COMPLETE = "legacy_scan_complete"
        private val LEGACY_NAME = Regex("ScreenRecording\\d+\\.mp4", RegexOption.IGNORE_CASE)
    }
}

internal object RecordingThumbnailCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized fun get(uri: Uri): Bitmap? = cache.get(uri.toString())
    @Synchronized fun put(uri: Uri, bitmap: Bitmap) = cache.put(uri.toString(), bitmap)
    @Synchronized fun remove(uri: Uri) = cache.remove(uri.toString())

    fun load(context: Context, uri: Uri, cancellation: CancellationSignal): Bitmap? {
        get(uri)?.let { return it }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(320, 180), cancellation)
            } else {
                val retriever = MediaMetadataRetriever()
                try { retriever.setDataSource(context, uri); retriever.frameAtTime } finally { retriever.release() }
            }
        }.getOrNull()
        if (bitmap != null) put(uri, bitmap)
        return bitmap
    }
}
