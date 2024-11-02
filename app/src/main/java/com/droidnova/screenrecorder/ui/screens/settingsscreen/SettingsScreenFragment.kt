package com.droidnova.screenrecorder.ui.screens.settingsscreen

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.databinding.FragmentSettingsScreenBinding
import com.droidnova.screenrecorder.extension.showToast

class SettingsScreenFragment : Fragment() {

    private var binding: FragmentSettingsScreenBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentSettingsScreenBinding.inflate(inflater,container,false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCards()
    }

    private fun setupCards() {
        setupOtherSettingsCard()
    }

    private fun setupOtherSettingsCard() {
        binding?.llRateUs?.setOnClickListener {
            openPlayStore()
        }
        binding?.llShareApp?.setOnClickListener {
            shareApp()
        }
        binding?.llPrivacyPolicy?.setOnClickListener {
            openPrivacyPolicy()
        }
        binding?.llReportBugs?.setOnClickListener {
            sendFeedback()
        }
        binding?.llJoinWpCommunity?.setOnClickListener {
            joinWhatsappCommunity()
        }

        //version name
        context?.let {
            val versionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.packageManager.getPackageInfo(it.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                it.packageManager.getPackageInfo(it.packageName, 0).versionName
            }
            binding?.tvVersionInfo?.text = "Version: $versionName"
        }
    }

    private fun joinWhatsappCommunity() {
        val communityLink = "https://chat.whatsapp.com/KpIySFpRFcoJam6aSI9cq5"
        try {
            context?.packageManager?.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
            context?.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(communityLink) })
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("myTag", "whatsapp not installed")
            context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(communityLink)))
        }
    }

    private fun openPlayStore() {
        val appPackageName = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }
    }

    private fun shareApp() {
        val appPackageName = requireContext().packageName
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out this app: https://play.google.com/store/apps/details?id=$appPackageName")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share app via"))
    }

    private fun openPrivacyPolicy() {
        val privacyPolicyUrl = "https://sites.google.com/view/screenrecorder0/home"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))

        // Check if there is an app available to handle the intent
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            // Handle the case where no app is available
            context.showToast("No browser found to open the link.")
        }
    }


    private fun sendFeedback() {
        val emailAddress = "droidnova7@gmail.com"
        val subject = "Feedback for Your App"

        // Create an intent with the mailto URI scheme
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$emailAddress")
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        try {
            startActivity(Intent.createChooser(emailIntent, "Send feedback"))
        } catch (e: android.content.ActivityNotFoundException) {
            context?.showToast("No email clients installed.")
        }
    }
}