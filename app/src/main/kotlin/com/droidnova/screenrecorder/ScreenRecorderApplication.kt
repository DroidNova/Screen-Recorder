package com.droidnova.screenrecorder

import android.app.Application
import com.droidnova.screenrecorder.ads.AdsController
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScreenRecorderApplication : Application() {
    internal val adsController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AdsController(this) }
}
