package com.droidnova.screenrecorder.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ScreenRecorderAppTest {
    @get:Rule val composeRule = createComposeRule()

    private fun launch() = composeRule.setContent { ScreenRecorderApp() }

    @Test fun startsOnHomeWithSelectedNavigationAndDisabledRecording() {
        launch()
        composeRule.onNodeWithText("Ready when recording arrives").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Start recording").assertIsNotEnabled()
    }

    @Test fun navigatesAcrossEveryTopLevelDestination() {
        launch()
        composeRule.onNodeWithText("Recordings").performClick()
        composeRule.onNodeWithText("No recordings yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Recording library").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Capture preset").assertIsDisplayed()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Capture summary").assertIsDisplayed()
    }

    @Test fun repeatedDestinationTapKeepsOneVisibleSelection() {
        launch()
        composeRule.onNodeWithText("Recordings").performClick().performClick()
        composeRule.onNodeWithText("Recordings").assertIsSelected()
        composeRule.onNodeWithText("No recordings yet").assertIsDisplayed()
    }
}
