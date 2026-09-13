package com.droidnova.screenrecorder.rating

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.ratePromptDataStore by preferencesDataStore("rate_prompt_preferences")

class RatePromptRepository(context: Context) {
    private val dataStore = context.applicationContext.ratePromptDataStore

    val state: Flow<RatePromptPersistence> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { values ->
            RatePromptPersistence(
                appOpenCount = values[APP_OPEN_COUNT] ?: 0,
                completed = values[COMPLETED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun recordLaunch(): RatePromptPersistence {
        var updated = RatePromptPersistence()
        dataStore.edit { values ->
            val count = (values[APP_OPEN_COUNT] ?: 0) + 1
            values[APP_OPEN_COUNT] = count
            updated = RatePromptPersistence(count, values[COMPLETED] ?: false)
        }
        return updated
    }

    suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val APP_OPEN_COUNT = intPreferencesKey("app_open_count")
        val COMPLETED = booleanPreferencesKey("rate_prompt_completed")
    }
}
