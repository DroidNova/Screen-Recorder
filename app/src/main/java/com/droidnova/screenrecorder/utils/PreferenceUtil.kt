package com.droidnova.screenrecorder.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object PreferenceUtil {

    private const val PREFERENCE_MANAGER = "d4f5d7f8d8fd5g75sd4s5f4s5d4s54f5s"
    private const val LAUNCH_COUNT = "j43lk5j43lk5j34l5k43j5lk34"
    private const val START_TIME = "df5df4f5d4fd5dff5fd"
    private const val CAMERA_TYPE = "d5df4df5f4d5fd4ddf5fdf54df"
    private const val RECORDING_TIME = "recordingTime"
    private const val KEY_IS_SERVICE_RUNNING = "is_service_running"
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

    var selectedCameraType: Int
        get() = mPref.getInt(CAMERA_TYPE, 1)
        set(value) = mPref.edit().putInt(CAMERA_TYPE, value).apply()

    var startTime: Long
        get() = mPref.getLong(START_TIME, -1)
        set(value) = mPref.edit().putLong(START_TIME, value).apply()

    var recordingTime: Int
        get() = mPref.getInt(RECORDING_TIME, 10)
        set(value) = mPref.edit().putInt(RECORDING_TIME, value).apply()

    var isServiceRunning: Boolean
        get() = mPref.getBoolean(KEY_IS_SERVICE_RUNNING, false)
        set(value) = mPref.edit().putBoolean(KEY_IS_SERVICE_RUNNING, value).apply()

    var videoMicrophoneState: Boolean
        get() = mPref.getBoolean(KEY_VIDEO_MICROPHONE_STATE, true)
        set(value) = mPref.edit().putBoolean(KEY_VIDEO_MICROPHONE_STATE, value).apply()


    var isAppLocked: Boolean
        get() = mPref.getBoolean(KEY_IS_APP_LOCKED, false)
        set(value) = mPref.edit().putBoolean(KEY_IS_APP_LOCKED, value).apply()

    var appPassword: String?
        get() = mPref.getString(KEY_APP_PASSWORD, null)
        set(value) = mPref.edit().putString(KEY_APP_PASSWORD, value).apply()

    var showRateUsCard: Boolean
        get() = mPref.getBoolean(KEY_SHOW_RATE_US_CARD, true)
        set(value) = mPref.edit().putBoolean(KEY_SHOW_RATE_US_CARD, value).apply()

    var showPreview: Boolean
        get() = mPref.getBoolean(KEY_SHOW_PREVIEW, false)
        set(value) = mPref.edit().putBoolean(KEY_SHOW_PREVIEW, value).apply()

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

    var securityQuestionAndAnswer: Pair<String, String>?
        get() {
            val combined = mPref.getString(KEY_SECURITY_QUESTION_ANSWER, null)
            return combined?.split("||")?.let {
                if (it.size == 2) Pair(it[0], it[1]) else null
            }
        }
        set(value) {
            val combined = value?.let { "${it.first}||${it.second}" }
            mPref.edit().putString("security_question_answer", combined).apply()
        }

    var selectedAudioInputType: String
        get() = mPref.getString(SELECTED_AUDIO_INPUT_TYPE, "Mic") ?: "Mic"
        set(value) = mPref.edit().putString(SELECTED_AUDIO_INPUT_TYPE, value).apply()



}

