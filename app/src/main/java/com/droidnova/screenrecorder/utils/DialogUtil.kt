package com.droidnova.screenrecorder.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.droidnova.screenrecorder.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogUtil {

    fun showLoadingDialog(context: Context,message: String): androidx.appcompat.app.AlertDialog {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress_loading, null)
        dialogView.findViewById<TextView>(R.id.tv_loading_message).text = message

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Material3_Dialog)
            .setView(dialogView)
            .setCancelable(false) // Prevent dismissing the dialog by tapping outside or back button
            .create()

        dialog.show()
        return dialog
    }
}