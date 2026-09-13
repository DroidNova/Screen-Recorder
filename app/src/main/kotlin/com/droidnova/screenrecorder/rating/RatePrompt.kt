package com.droidnova.screenrecorder.rating

data class RatePromptPersistence(
    val appOpenCount: Int = 0,
    val completed: Boolean = false,
)

enum class RatePromptAction { None, Feedback, PlayStore }

fun ratePromptAction(stars: Int): RatePromptAction = when (stars) {
    in 1..4 -> RatePromptAction.Feedback
    5 -> RatePromptAction.PlayStore
    else -> RatePromptAction.None
}

fun isRatePromptEligible(
    appOpenCount: Int,
    launchRecorded: Boolean,
    completed: Boolean,
    dismissedThisSession: Boolean,
): Boolean = launchRecorded &&
    !completed &&
    !dismissedThisSession &&
    appOpenCount > 0 &&
    appOpenCount % 3 == 0
