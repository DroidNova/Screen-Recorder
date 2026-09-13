package com.droidnova.screenrecorder.rating

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatePromptRepositoryTest {
    @Test
    fun launchCountIncrementsAndCompletionIsStored() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = RatePromptRepository(context)
        val before = repository.state.first().appOpenCount

        val afterLaunch = repository.recordLaunch()
        repository.markCompleted()
        val persisted = repository.state.first { it.completed }

        assertEquals(before + 1, afterLaunch.appOpenCount)
        assertEquals(afterLaunch.appOpenCount, persisted.appOpenCount)
        assertTrue(persisted.completed)
    }
}
