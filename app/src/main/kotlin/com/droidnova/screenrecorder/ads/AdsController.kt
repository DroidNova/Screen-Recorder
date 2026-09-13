package com.droidnova.screenrecorder.ads

import android.app.Activity
import android.app.Application
import com.droidnova.screenrecorder.BuildConfig
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AdsUiState(
    val configured: Boolean = false,
    val consentAllowsAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

internal fun shouldShowBanner(
    runtimeState: RecordingState,
    startFlowInProgress: Boolean,
    consentAllowsAds: Boolean,
    adsConfigured: Boolean,
    appInForeground: Boolean,
    overlayVisible: Boolean,
    recoveryProcessing: Boolean,
): Boolean = adsConfigured && consentAllowsAds && appInForeground &&
    runtimeState == RecordingState.Idle && !startFlowInProgress && !overlayVisible && !recoveryProcessing

/** Process-owned consent and SDK initialization state; never retains an Activity. */
internal class AdsController(private val application: Application) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(application)
    private val initialized = AtomicBoolean(false)
    private val collapsibleRequestClaimed = AtomicBoolean(false)
    private val adsConfigured = BuildConfig.ADMOB_ENABLED && BuildConfig.ADMOB_BANNER_AD_UNIT_ID.isNotBlank()
    private val _state = MutableStateFlow(AdsUiState(configured = adsConfigured))
    val state = _state.asStateFlow()

    fun requestConsent(activity: Activity) {
        if (!adsConfigured) return
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    publishConsentState()
                }
                // A prior valid consent state can permit requests before a form callback.
                publishConsentState()
            },
            {
                // UMP is authoritative when an update fails; only its cached result is used.
                publishConsentState()
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        if (_state.value.privacyOptionsRequired) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { publishConsentState() }
        }
    }

    fun claimCollapsibleRequest(): Boolean = collapsibleRequestClaimed.compareAndSet(false, true)

    private fun publishConsentState() {
        val canRequest = consentInformation.canRequestAds()
        _state.value = AdsUiState(
            configured = adsConfigured,
            consentAllowsAds = canRequest,
            privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        )
        if (canRequest && initialized.compareAndSet(false, true)) {
            MobileAds.initialize(application)
        }
    }
}
