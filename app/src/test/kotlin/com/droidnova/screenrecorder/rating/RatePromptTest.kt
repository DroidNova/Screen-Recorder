package com.droidnova.screenrecorder.rating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatePromptTest {
    @Test
    fun `eligible on every third positive launch`() {
        assertTrue(isRatePromptEligible(3, launchRecorded = true, completed = false, dismissedThisSession = false))
        assertTrue(isRatePromptEligible(6, launchRecorded = true, completed = false, dismissedThisSession = false))
        listOf(0, 2, 4).forEach {
            assertFalse(isRatePromptEligible(it, launchRecorded = true, completed = false, dismissedThisSession = false))
        }
    }

    @Test
    fun `hidden until launch persistence completes`() {
        assertFalse(isRatePromptEligible(3, launchRecorded = false, completed = false, dismissedThisSession = false))
    }

    @Test
    fun `completion and session dismissal suppress prompt`() {
        assertFalse(isRatePromptEligible(3, launchRecorded = true, completed = true, dismissedThisSession = false))
        assertFalse(isRatePromptEligible(3, launchRecorded = true, completed = false, dismissedThisSession = true))
    }

    @Test
    fun `star selection maps to destination`() {
        assertEquals(RatePromptAction.None, ratePromptAction(0))
        (1..4).forEach { assertEquals(RatePromptAction.Feedback, ratePromptAction(it)) }
        assertEquals(RatePromptAction.PlayStore, ratePromptAction(5))
    }
}
