package com.droidnova.screenrecorder.recording

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.recordingPreferencesDataStore by preferencesDataStore("recording_preferences")

data class RecordingPreferences(
    val preset: RecordingPreset = RecordingPreset.Balanced,
    val customShortEdge: Int? = null,
    val customFps: Int? = null,
    val customBitrate: Int? = null,
    val audioMode: AudioMode = AudioMode.None,
    val countdownSeconds: Int = 0,
    val notificationRequestedBefore: Boolean = false,
    val audioRequestedBefore: Boolean = false,
)

class RecordingPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.recordingPreferencesDataStore
    val preferences: Flow<RecordingPreferences> = dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { values ->
        RecordingPreferences(
            preset = enumOrDefault(values[PRESET], RecordingPreset.Balanced),
            customShortEdge = values[SHORT_EDGE], customFps = values[FPS], customBitrate = values[BITRATE],
            audioMode = enumOrDefault(values[AUDIO], AudioMode.None),
            countdownSeconds = values[COUNTDOWN]?.takeIf { it in setOf(0, 3, 5, 15) } ?: 0,
            notificationRequestedBefore = values[NOTIFICATION_REQUESTED] ?: false,
            audioRequestedBefore = values[AUDIO_REQUESTED] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun saveVideo(configuration: AvailableVideoConfiguration) = dataStore.edit {
        it[PRESET] = configuration.preset.name
        if (configuration.preset == RecordingPreset.Custom) {
            it[SHORT_EDGE] = configuration.shortEdge; it[FPS] = configuration.frameRate.framesPerSecond
            it[BITRATE] = configuration.bitrate.bitsPerSecond
        }
    }
    suspend fun saveAudio(mode: AudioMode) = dataStore.edit { it[AUDIO] = mode.name }
    suspend fun saveCountdown(seconds: Int) = dataStore.edit { it[COUNTDOWN] = seconds }
    suspend fun markNotificationRequested() = dataStore.edit { it[NOTIFICATION_REQUESTED] = true }
    suspend fun markAudioRequested() = dataStore.edit { it[AUDIO_REQUESTED] = true }
    suspend fun reset() = dataStore.edit {
        it.remove(PRESET); it.remove(SHORT_EDGE); it.remove(FPS); it.remove(BITRATE); it.remove(AUDIO); it.remove(COUNTDOWN)
    }

    private companion object {
        val PRESET = stringPreferencesKey("quality_preset")
        val SHORT_EDGE = intPreferencesKey("custom_resolution_short_edge")
        val FPS = intPreferencesKey("custom_fps")
        val BITRATE = intPreferencesKey("custom_video_bitrate")
        val AUDIO = stringPreferencesKey("audio_source")
        val COUNTDOWN = intPreferencesKey("countdown_seconds")
        val NOTIFICATION_REQUESTED = booleanPreferencesKey("notification_permission_requested_before")
        val AUDIO_REQUESTED = booleanPreferencesKey("record_audio_permission_requested_before")
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
