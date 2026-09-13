package com.droidnova.screenrecorder.ads

import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.droidnova.screenrecorder.BuildConfig
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

internal enum class BannerLoadState { NotRequested, Loading, Loaded, Failed }

@Composable
internal fun CollapsibleBanner(
    availableWidth: Dp,
    eligible: Boolean,
    claimCollapsibleRequest: () -> Boolean,
    onLoadStateChanged: (BannerLoadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val adView = remember {
        AdView(context).apply { adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID }
    }
    var requested by remember { mutableStateOf(false) }
    var reservedHeight by remember { mutableStateOf(50.dp) }

    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (eligible) adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            (adView.parent as? ViewGroup)?.removeView(adView)
            adView.destroy()
        }
    }

    LaunchedEffect(eligible) {
        if (eligible) adView.resume() else adView.pause()
    }
    LaunchedEffect(eligible, availableWidth) {
        if (!eligible || requested || availableWidth <= 0.dp) return@LaunchedEffect
        requested = true
        onLoadStateChanged(BannerLoadState.Loading)
        val width = availableWidth.value.toInt().coerceAtLeast(1)
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width)
        reservedHeight = adaptiveSize.height.dp
        adView.setAdSize(adaptiveSize)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() = onLoadStateChanged(BannerLoadState.Loaded)
            override fun onAdFailedToLoad(error: LoadAdError) = onLoadStateChanged(BannerLoadState.Failed)
        }
        val requestBuilder = AdRequest.Builder()
        if (claimCollapsibleRequest()) {
            requestBuilder.addNetworkExtrasBundle(
                AdMobAdapter::class.java,
                Bundle().apply { putString("collapsible", "bottom") },
            )
        }
        adView.loadAd(requestBuilder.build())
    }

    if (eligible) {
        Box(modifier.fillMaxWidth().height(reservedHeight).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = {
                    (adView.parent as? ViewGroup)?.removeView(adView)
                    adView
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
