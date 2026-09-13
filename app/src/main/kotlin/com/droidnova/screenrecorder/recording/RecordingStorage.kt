package com.droidnova.screenrecorder.recording

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.droidnova.screenrecorder.domain.recording.AudioMode
import java.io.File

internal data class StoragePolicy(
    val estimatedBytesPerSecond: Long,
    val minimumStartBytes: Long,
    val lowStorageStopBytes: Long,
)

internal sealed interface StorageCheck {
    data class Available(val bytes: Long, val policy: StoragePolicy) : StorageCheck
    data class Insufficient(val bytes: Long, val policy: StoragePolicy) : StorageCheck
    data object Unavailable : StorageCheck
}

/** Single source of truth for the output volume and recording storage reserves. */
internal class RecordingStorage(private val context: Context) {
    fun policy(videoBitrate: Int, audioMode: AudioMode): StoragePolicy {
        val audio = if (audioMode == AudioMode.None) 0L else AUDIO_BITRATE_BITS_PER_SECOND
        val payload = saturatedAdd(videoBitrate.toLong(), audio) / 8L
        val estimated = saturatedAdd(payload, (payload * CONTAINER_OVERHEAD_PERCENT) / 100L)
        return StoragePolicy(
            estimatedBytesPerSecond = estimated,
            minimumStartBytes = maxOf(MINIMUM_START_FLOOR_BYTES, saturatedAdd(saturatedMultiply(estimated, 60L), START_FINALIZATION_RESERVE_BYTES)),
            lowStorageStopBytes = maxOf(LOW_STORAGE_FLOOR_BYTES, saturatedAdd(saturatedMultiply(estimated, 30L), STOP_FINALIZATION_RESERVE_BYTES)),
        )
    }

    fun check(videoBitrate: Int, audioMode: AudioMode, starting: Boolean = true): StorageCheck {
        val policy = policy(videoBitrate, audioMode)
        val directory = outputVolumeDirectory() ?: return StorageCheck.Unavailable
        return try {
            val statAvailable = StatFs(directory.absolutePath).availableBytes
            val allocatable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(StorageManager::class.java)
                runCatching { manager.getAllocatableBytes(manager.getUuidForPath(directory)) }.getOrNull()
            } else null
            val available = allocatable?.let { minOf(statAvailable, it) } ?: statAvailable
            val threshold = if (starting) policy.minimumStartBytes else policy.lowStorageStopBytes
            if (available >= threshold) StorageCheck.Available(available, policy)
            else StorageCheck.Insufficient(available, policy)
        } catch (_: Exception) {
            StorageCheck.Unavailable
        }
    }

    fun availableBytes(): Long? = when (val result = check(0, AudioMode.None, starting = false)) {
        is StorageCheck.Available -> result.bytes
        is StorageCheck.Insufficient -> result.bytes
        StorageCheck.Unavailable -> null
    }

    fun outputVolumeDirectory(): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.takeIf { it.exists() || it.mkdirs() }

    companion object {
        const val CHECK_INTERVAL_MILLIS = 30_000L
        const val AUDIO_BITRATE_BITS_PER_SECOND = 128_000L
        private const val MIB = 1024L * 1024L
        const val MINIMUM_START_FLOOR_BYTES = 100L * MIB
        const val START_FINALIZATION_RESERVE_BYTES = 32L * MIB
        const val LOW_STORAGE_FLOOR_BYTES = 64L * MIB
        const val STOP_FINALIZATION_RESERVE_BYTES = 16L * MIB
        private const val CONTAINER_OVERHEAD_PERCENT = 5L

        private fun saturatedAdd(a: Long, b: Long): Long = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
        private fun saturatedMultiply(a: Long, b: Long): Long = if (a != 0L && Long.MAX_VALUE / a < b) Long.MAX_VALUE else a * b
    }
}
