package com.droidnova.screenrecorder.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.databinding.DialogFpsSelectorBinding
import com.droidnova.screenrecorder.databinding.DialogQualitySelectorBinding
import com.droidnova.screenrecorder.databinding.DialogResolutionSelectorBinding
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

    fun showQualitySelectorDialog(
        context: Context,
        selectedQuality: String,
        onQualitySelected: (String) -> Unit
    ) {
        val binding = DialogQualitySelectorBinding.inflate(LayoutInflater.from(context))

        // Map layout to radio buttons and tags for each quality
        val layoutToComponentsMap = hashMapOf(
            binding.ll3mbps to Pair(binding.radio3mbps, binding.text3mbps),
            binding.ll6mbps to Pair(binding.radio6mbps, binding.text6mbps),
            binding.ll8mbps to Pair(binding.radio8mbps, binding.text8mbps),
            binding.ll10mbps to Pair(binding.radio10mbps, binding.text10mbps),
            binding.ll12mbps to Pair(binding.radio12mbps, binding.text12mbps),
            binding.ll15mbps to Pair(binding.radio15mbps, binding.text15mbps)
        )

        // Track the currently selected quality
        var tempSelectedQuality = selectedQuality

        // Set visibility, checked states, and listeners
        layoutToComponentsMap.forEach { (layout, pair) ->
            val (radioButton, _) = pair
            layout.visibility = View.VISIBLE
            radioButton.isChecked = layout.tag == selectedQuality // Compare with layout.tag

            layout.setOnClickListener {
                layoutToComponentsMap.values.forEach { it.first.isChecked = false }
                radioButton.isChecked = true
                tempSelectedQuality = layout.tag.toString() // Store the selected tag
            }
        }

        // Build and display the dialog
        MaterialAlertDialogBuilder(context)
            .setTitle("Select Video Quality")
            .setView(binding.root)
            .setPositiveButton("OK") { _, _ ->
                onQualitySelected(tempSelectedQuality) // Callback with selected tag value
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    fun showResolutionSelectorDialog(
        context: Context,
        selectedResolution: String,
        onResolutionSelected: (String) -> Unit
    ) {
        val binding = DialogResolutionSelectorBinding.inflate(LayoutInflater.from(context))

        // Map layout to radio buttons and tags for each resolution
        val layoutToComponentsMap = hashMapOf(
            binding.ll360p to Pair(binding.radio360p, binding.text360p),
            binding.ll480p to Pair(binding.radio480p, binding.text480p),
            binding.ll540p to Pair(binding.radio540p, binding.text540p),
            binding.ll640p to Pair(binding.radio640p, binding.text640p),
            binding.ll720p to Pair(binding.radio720p, binding.text720p),
            binding.ll1080p to Pair(binding.radio1080p, binding.text1080p)
        )

        // Track the currently selected resolution
        var tempSelectedResolution = selectedResolution

        // Set visibility, checked states, and listeners
        layoutToComponentsMap.forEach { (layout, pair) ->
            val (radioButton, _) = pair
            layout.visibility = View.VISIBLE
            radioButton.isChecked = layout.tag == selectedResolution // Compare with layout.tag

            layout.setOnClickListener {
                layoutToComponentsMap.values.forEach { it.first.isChecked = false }
                radioButton.isChecked = true
                tempSelectedResolution = layout.tag.toString() // Store the selected tag
            }
        }

        // Build and display the dialog
        MaterialAlertDialogBuilder(context)
            .setTitle("Select Resolution")
            .setView(binding.root)
            .setPositiveButton("OK") { _, _ ->
                onResolutionSelected(tempSelectedResolution) // Callback with selected tag value
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    fun showFpsSelectorDialog(
        context: Context,
        selectedFps: String,
        onFpsSelected: (String) -> Unit
    ) {
        val binding = DialogFpsSelectorBinding.inflate(LayoutInflater.from(context))

        // Map layout to radio buttons and FPS values
        val layoutToComponentsMap = hashMapOf(
            binding.ll30fps to Pair(binding.radio30fps, binding.text30fps),
            binding.ll60fps to Pair(binding.radio60fps, binding.text60fps)
        )

        // Track the currently selected FPS
        var tempSelectedFps = selectedFps

        // Set visibility, checked states, and listeners
        layoutToComponentsMap.forEach { (layout, pair) ->
            val (radioButton, _) = pair
            layout.visibility = View.VISIBLE
            radioButton.isChecked = layout.tag == selectedFps // Compare with layout.tag

            layout.setOnClickListener {
                layoutToComponentsMap.values.forEach { it.first.isChecked = false }
                radioButton.isChecked = true
                tempSelectedFps = layout.tag.toString() // Store the selected tag
            }
        }

        // Build and display the dialog
        MaterialAlertDialogBuilder(context)
            .setTitle("Select FPS")
            .setView(binding.root)
            .setPositiveButton("OK") { _, _ ->
                onFpsSelected(tempSelectedFps) // Callback with selected tag value
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


}