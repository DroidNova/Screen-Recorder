package com.droidnova.screenrecorder

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.droidnova.screenrecorder.databinding.ActivityMainBinding
import com.droidnova.screenrecorder.utils.PreferenceUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        PreferenceUtil.init(this)

        setUpNavigation()
        MobileAds.initialize(this@MainActivity) { }
        initAdview()

    }

    private fun initAdview() {
        val adView = AdView(this).apply {
            adUnitId = "ca-app-pub-4788231589271799/2948796263"
//            adUnitId = "ca-app-pub-3940256099942544/9214589741"
        }
        adView.setAdSize(AdSize.BANNER)
        binding?.bannerAdView?.addView(adView)
        try {
            loadAd(adView)
        }catch (e:Exception){
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun loadAd(adView: AdView, retryCount: Int = 3) {
        if (retryCount <= 0) return

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        adView.adListener = object: AdListener() {
            override fun onAdFailedToLoad(adError : LoadAdError) {
                // Code to be executed when an ad request fails.
                if (retryCount > 0) {
                    Log.d("myTag", "Retrying to load ad...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadAd(adView,retryCount - 1)
                    }, 5000L)
                }

            }
        }
    }

    private fun setUpNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
        navHostFragment?.run {
            binding?.bottomNav?.let { NavigationUI.setupWithNavController(it, navController) }
        }
    }
}