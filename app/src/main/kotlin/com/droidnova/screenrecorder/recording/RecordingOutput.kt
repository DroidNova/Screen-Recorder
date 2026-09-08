package com.droidnova.screenrecorder.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

internal enum class OutputPhase { WRITING, READY_TO_PUBLISH, DISCARD_REQUIRED }

internal data class PendingOutput(val location: String, val displayName: String, val startedAt: Long, val phase: OutputPhase)

enum class RecordingOutcomeType { IncompleteRecordingRemoved, RecoveredRecordingSaved, StorageLow, FinalizationFailed }

data class PendingRecordingOutcome(val id: Long, val type: RecordingOutcomeType)

internal class PendingOutputJournal(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): PendingOutput? {
        if (!preferences.contains(KEY_VERSION)) return null
        if (preferences.getInt(KEY_VERSION, -1) != VERSION) return clear().let { null }
        val location = preferences.getString(KEY_LOCATION, null) ?: return clear().let { null }
        val name = preferences.getString(KEY_NAME, null) ?: return clear().let { null }
        val phase = preferences.getString(KEY_PHASE, null)?.let { runCatching { OutputPhase.valueOf(it) }.getOrNull() }
            ?: return clear().let { null }
        if (!RecordingFilename.isAppRecording(name)) return clear().let { null }
        return PendingOutput(location, name, preferences.getLong(KEY_STARTED, 0L), phase)
    }

    fun write(entry: PendingOutput): Boolean = preferences.edit()
        .putInt(KEY_VERSION, VERSION).putString(KEY_LOCATION, entry.location)
        .putString(KEY_NAME, entry.displayName).putLong(KEY_STARTED, entry.startedAt)
        .putString(KEY_PHASE, entry.phase.name).commit()

    fun clear(): Boolean = journalRemoval(preferences.edit()).commit()

    fun readOutcome(): PendingRecordingOutcome? {
        val id = preferences.getLong(KEY_OUTCOME_ID, 0L).takeIf { it > 0L } ?: return null
        val type = preferences.getString(KEY_OUTCOME_TYPE, null)?.let {
            runCatching { RecordingOutcomeType.valueOf(it) }.getOrNull()
        } ?: return null
        return PendingRecordingOutcome(id, type)
    }

    fun storeOutcome(type: RecordingOutcomeType): PendingRecordingOutcome {
        val outcome = PendingRecordingOutcome(nextOutcomeId(), type)
        check(preferences.edit().putLong(KEY_OUTCOME_ID, outcome.id).putString(KEY_OUTCOME_TYPE, type.name).commit())
        return outcome
    }

    fun resolveWithOutcome(type: RecordingOutcomeType): PendingRecordingOutcome {
        val outcome = PendingRecordingOutcome(nextOutcomeId(), type)
        check(journalRemoval(preferences.edit())
            .putLong(KEY_OUTCOME_ID, outcome.id).putString(KEY_OUTCOME_TYPE, type.name).commit())
        return outcome
    }

    fun acknowledgeOutcome(id: Long): Boolean {
        if (preferences.getLong(KEY_OUTCOME_ID, 0L) != id) return false
        return preferences.edit().remove(KEY_OUTCOME_ID).remove(KEY_OUTCOME_TYPE).commit()
    }

    private fun journalRemoval(editor: android.content.SharedPreferences.Editor) = editor
        .remove(KEY_VERSION).remove(KEY_LOCATION).remove(KEY_NAME).remove(KEY_STARTED).remove(KEY_PHASE)

    private fun nextOutcomeId(): Long = maxOf(
        System.currentTimeMillis().coerceAtLeast(1L),
        preferences.getLong(KEY_OUTCOME_ID, 0L).let { if (it == Long.MAX_VALUE) 1L else it + 1L },
    )

    private companion object {
        const val PREFERENCES = "pending_recording_output"
        const val VERSION = 1
        const val KEY_VERSION = "schema_version"
        const val KEY_LOCATION = "location"
        const val KEY_NAME = "display_name"
        const val KEY_STARTED = "started_at"
        const val KEY_PHASE = "phase"
        const val KEY_OUTCOME_ID = "outcome_id"
        const val KEY_OUTCOME_TYPE = "outcome_type"
    }
}

internal class RecordingOutput private constructor(
    private val context: Context,
    private val entry: PendingOutput,
    private val journal: PendingOutputJournal,
    private val pendingUri: Uri?,
    private var descriptor: ParcelFileDescriptor?,
    private val partialFile: File?,
    private val finalFile: File?,
) {
    fun createMuxer(): MediaMuxer = if (pendingUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        MediaMuxer(requireNotNull(descriptor).fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    } else MediaMuxer(requireNotNull(partialFile).absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    fun publish() {
        check(journal.write(entry.copy(phase = OutputPhase.READY_TO_PUBLISH)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            check(context.contentResolver.update(requireNotNull(pendingUri), values, null, null) == 1)
        } else {
            check(requireNotNull(partialFile).renameTo(requireNotNull(finalFile)))
            MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), arrayOf(MIME_TYPE), null)
        }
        check(journal.clear())
    }

    fun discard() {
        journal.write(entry.copy(phase = OutputPhase.DISCARD_REQUIRED))
        val removed = if (pendingUri != null) context.contentResolver.delete(pendingUri, null, null) >= 0
        else partialFile?.let { !it.exists() || it.delete() } == true
        if (removed) journal.clear()
    }

    fun closeDescriptor() { descriptor?.close(); descriptor = null }

    companion object {
        const val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/Screen Recorder"
        const val MIME_TYPE = "video/mp4"

        fun create(context: Context, epochMillis: Long, collisionSequence: Long): RecordingOutput {
            val journal = PendingOutputJournal(context)
            check(journal.read() == null)
            val displayName = RecordingFilename.fromEpochMillis(epochMillis, collisionSequence)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName); put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
                    put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH); put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
                val entry = PendingOutput(uri.toString(), displayName, epochMillis, OutputPhase.WRITING)
                if (!journal.write(entry)) { context.contentResolver.delete(uri, null, null); error("journal unavailable") }
                val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                if (descriptor == null) { RecordingOutput(context, entry, journal, uri, null, null, null).discard(); error("output unavailable") }
                return RecordingOutput(context, entry, journal, uri, descriptor, null, null)
            }
            val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES))
            val partialDirectory = File(directory, ".pending").apply { check(exists() || mkdirs()) }
            val partial = File(partialDirectory, "$displayName.partial")
            val entry = PendingOutput(partial.absolutePath, displayName, epochMillis, OutputPhase.WRITING)
            check(journal.write(entry))
            return RecordingOutput(context, entry, journal, null, null, partial, File(directory, displayName))
        }

        fun recover(context: Context): PendingRecordingOutcome? {
            val journal = PendingOutputJournal(context)
            val entry = journal.read()
            if (entry != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) recoverMediaStore(context, journal, entry)
                else recoverLegacy(context, journal, entry)
            }
            return journal.readOutcome()
        }

        private fun recoverMediaStore(context: Context, journal: PendingOutputJournal, entry: PendingOutput) {
            val uri = runCatching { Uri.parse(entry.location) }.getOrNull()
            if (uri == null || uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY ||
                uri.pathSegments.takeLast(2).firstOrNull() != "media" || uri.lastPathSegment?.toLongOrNull() == null
            ) {
                journal.clear(); return
            }
            val projection = arrayOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.RELATIVE_PATH)
            val valid = context.contentResolver.query(uri, projection, null, null, null)?.use {
                it.moveToFirst() && it.getString(0) == entry.displayName && it.getString(1) == MIME_TYPE &&
                    it.getString(2)?.trimEnd('/') == RELATIVE_PATH
            } == true
            if (!valid) { journal.clear(); return }
            if (entry.phase == OutputPhase.READY_TO_PUBLISH) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                if (context.contentResolver.update(uri, values, null, null) == 1) {
                    journal.resolveWithOutcome(RecordingOutcomeType.RecoveredRecordingSaved)
                }
            } else {
                if (context.contentResolver.delete(uri, null, null) >= 0) {
                    journal.resolveWithOutcome(RecordingOutcomeType.IncompleteRecordingRemoved)
                }
            }
        }

        private fun recoverLegacy(context: Context, journal: PendingOutputJournal, entry: PendingOutput) {
            val directory = File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)), ".pending").canonicalFile
            val file = runCatching { File(entry.location).canonicalFile }.getOrNull()
            if (file == null || file.parentFile != directory || !file.name.endsWith(".partial")) { journal.clear(); return }
            if (!file.exists() || file.delete()) journal.resolveWithOutcome(RecordingOutcomeType.IncompleteRecordingRemoved)
        }
    }
}
