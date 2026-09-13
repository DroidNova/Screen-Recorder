package com.droidnova.screenrecorder.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.droidnova.screenrecorder.R
import java.util.Locale

object IntentUtils {
    private const val SUPPORT_EMAIL = "droidnova7@gmail.com"

    fun rateUs(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: ActivityNotFoundException) {
            openPlayStoreInBrowser(context, packageName)
        } catch (_: SecurityException) {
            openPlayStoreInBrowser(context, packageName)
        }
    }

    private fun openPlayStoreInBrowser(context: Context, packageName: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri()
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.play_store_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareApp(context: Context) {
        val url = "https://play.google.com/store/apps/details?id=${context.packageName}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_message, url))
            type = "text/plain"
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_app_via)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun fetchAppVersion(context: Context): String = runCatching {
        packageInfo(context.packageManager, context.packageName)?.versionName
    }.getOrNull() ?: context.getString(R.string.version_unknown)

    fun sendFeedback(context: Context) {
        sendSupportEmail(context, "App Feedback", "Write your feedback below this line")
    }

    fun reportBugs(context: Context) {
        sendSupportEmail(context, "Bug Report", "Describe the issue below this line")
    }

    fun applyAppLocale(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    private fun sendSupportEmail(context: Context, subjectPrefix: String, prompt: String) {
        val packageManager = context.packageManager
        val appName = context.applicationInfo.loadLabel(packageManager).toString()
        val version = packageInfo(packageManager, context.packageName)?.versionName ?: "Unknown"
        val brand = Build.BRAND.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
        val body = """
            Device: $brand ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})

            --- $prompt ---
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "$subjectPrefix - $appName ($version)")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isPackageInstalled("com.google.android.gm", context)) setPackage("com.google.android.gm")
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.no_email_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packageName: String, context: Context): Boolean =
        packageInfo(context.packageManager, packageName) != null

    @Suppress("DEPRECATION")
    private fun packageInfo(packageManager: PackageManager, packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            }.getOrNull()
        } else {
            runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        }
}
