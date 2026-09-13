package com.droidnova.screenrecorder.recording

import android.util.Log

/** Release-safe diagnostics: event categories only; never accepts user or output metadata. */
internal object RecorderLog {
    private const val TAG = "ScreenRecorder"

    fun warning(sessionId: Long, event: String) {
        Log.w(TAG, "session=$sessionId event=$event")
    }
}
