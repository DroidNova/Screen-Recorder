package com.droidnova.screenrecorder.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object PreferenceUtil {

    private const val PREFERENCE_MANAGER = "d4f5d7f8d8fd5g75sd4s5f4s5d4s54f5s"
    private const val LAUNCH_COUNT = "j43lk5j43lk5j34l5k43j5lk34"
    private const val KEY_VIDEO_MICROPHONE_STATE = "video_microphone_state"
    private const val KEY_IS_APP_LOCKED = "isAppLocked"
    private const val KEY_APP_PASSWORD = "appPassword"
    private const val KEY_SHOW_RATE_US_CARD = "showRateUsCard"
    private const val KEY_SHOW_PREVIEW = "show_preview"
    private const val KEY_MAX_VIDEO_TIME = "max_video_time"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FREE_PREMIUM_COUNT = "free_premium_count"
    private const val KEY_SHOW_SCOPED_STORAGE_DIALOG = "show_scoped_storage_dialog"
    private const val KEY_BOUGHT_PREMIUM = "j43lk5lk5j34l5k43j5hg5lk6j5lk34"
    private const val KEY_SECURITY_QUESTION_ANSWER = "security_question_answer"
    private const val SELECTED_AUDIO_INPUT_TYPE = "selected_audio_input_type"
    private const val KEY_VIDEO_QUALITY = "video_quality"
    private const val KEY_VIDEO_RESOLUTION = "video_resolution"
    private const val KEY_VIDEO_FPS = "video_fps"

    private lateinit var mPref: SharedPreferences

    fun init(context: Context) {
        if (!this::mPref.isInitialized){
            mPref = context.getSharedPreferences(PREFERENCE_MANAGER, Context.MODE_PRIVATE)
            Log.d("PreferenceUtil", "initialized ")
        }

    }

    var launchCount: Int
        get() = mPref.getInt(LAUNCH_COUNT, -1)
        set(value) = mPref.edit().putInt(LAUNCH_COUNT, value).apply()

    var videoMicrophoneState: Boolean
        get() = mPref.getBoolean(KEY_VIDEO_MICROPHONE_STATE, true)
        set(value) = mPref.edit().putBoolean(KEY_VIDEO_MICROPHONE_STATE, value).apply()

    var showRateUsCard: Boolean
        get() = mPref.getBoolean(KEY_SHOW_RATE_US_CARD, true)
        set(value) = mPref.edit().putBoolean(KEY_SHOW_RATE_US_CARD, value).apply()

    var maxVideoTime: Int
        get() = mPref.getInt(KEY_MAX_VIDEO_TIME,2700)
        set(value) = mPref.edit().putInt(KEY_MAX_VIDEO_TIME, value).apply()

    var savedFolderUri: String?
        get() = mPref.getString(KEY_FOLDER_URI, null)
        set(value) = mPref.edit().putString(KEY_FOLDER_URI,value).apply()

    var freePremiumCount:Int
        get() = mPref.getInt(KEY_FREE_PREMIUM_COUNT,0)
        set(value) = mPref.edit().putInt(KEY_FREE_PREMIUM_COUNT,value).apply()

    var showScopedStorageDialog: Boolean
        get() = mPref.getBoolean(KEY_SHOW_SCOPED_STORAGE_DIALOG, true)
        set(value) = mPref.edit().putBoolean(KEY_SHOW_SCOPED_STORAGE_DIALOG, value).apply()

    var hasBoughtPremium: Boolean
        get() = mPref.getBoolean(KEY_BOUGHT_PREMIUM, false)
        set(value) = mPref.edit().putBoolean(KEY_BOUGHT_PREMIUM, value).apply()

    var selectedAudioInputType: String
        get() = mPref.getString(SELECTED_AUDIO_INPUT_TYPE, "Mic") ?: "Mic"
        set(value) = mPref.edit().putString(SELECTED_AUDIO_INPUT_TYPE, value).apply()

    // Add getter/setter for Video Quality
    var selectedVideoQuality: String
        get() = mPref.getString(KEY_VIDEO_QUALITY, "12Mbps") ?: "12Mbps"
        set(value) = mPref.edit().putString(KEY_VIDEO_QUALITY, value).apply()

    // Add getter/setter for Video Resolution
    var selectedVideoResolution: String
        get() = mPref.getString(KEY_VIDEO_RESOLUTION, "720p") ?: "720p"
        set(value) = mPref.edit().putString(KEY_VIDEO_RESOLUTION, value).apply()

    // Add getter/setter for Video FPS
    var selectedVideoFps: String
        get() = mPref.getString(KEY_VIDEO_FPS, "60fps") ?: "60fps"
        set(value) = mPref.edit().putString(KEY_VIDEO_FPS, value).apply()



}

