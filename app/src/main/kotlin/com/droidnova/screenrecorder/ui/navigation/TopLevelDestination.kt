package com.droidnova.screenrecorder.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.droidnova.screenrecorder.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val selectedIcon: Int,
    @DrawableRes val unselectedIcon: Int,
) {
    Home("home", R.string.nav_home, R.drawable.ic_home_filled, R.drawable.ic_home_outline),
    Recordings("recordings", R.string.nav_recordings, R.drawable.ic_recordings_filled, R.drawable.ic_recordings_outline),
    Settings("settings", R.string.nav_settings, R.drawable.ic_settings_filled, R.drawable.ic_settings_outline),
}
