package com.droidnova.screenrecorder.ui.bottom_sheets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.droidnova.screenrecorder.databinding.BottomSheetPermissionsBinding
import com.droidnova.screenrecorder.extension.showToast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PermissionBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetPermissionsBinding

    private lateinit var requestAudioPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestOverlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializePermissionLaunchers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        refreshCheckboxStates()
    }

    private fun initializePermissionLaunchers() {
        requestAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            refreshCheckboxStates()
        }

        requestOverlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            refreshCheckboxStates()
        }

        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            refreshCheckboxStates()
        }

    }

    private fun setupUI() {
        binding.layoutAudioPermission.setOnClickListener {
            binding.btnAllowAudioPermission.callOnClick()
        }
        binding.layoutOverlayPermission.setOnClickListener {
           binding. btnAllowOverlayPermission.callOnClick()
        }
        binding.layoutNotificationPermission.setOnClickListener {
            binding.btnAllowNotificationPermission.callOnClick()
        }

        binding.btnAllowAudioPermission.setOnClickListener {
            requestAudioPermission()
        }

        binding.btnAllowOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnAllowNotificationPermission.setOnClickListener {
            requestNotificationPermission()
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(requireContext())) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
            requestOverlayPermissionLauncher.launch(overlayIntent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshCheckboxStates() {
        val audioPermissionGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val overlayPermissionGranted = Settings.canDrawOverlays(requireContext())

        val notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        // Update visibility based on permission status
        binding.layoutAudioPermission.visibility = if (audioPermissionGranted) View.GONE else View.VISIBLE
        binding.layoutOverlayPermission.visibility = if (overlayPermissionGranted) View.GONE else View.VISIBLE
        binding.layoutNotificationPermission.visibility = if (notificationPermissionGranted) View.GONE else View.VISIBLE

        // Check if all permissions are granted to dismiss the BottomSheet
        if (audioPermissionGranted && overlayPermissionGranted &&
            notificationPermissionGranted
        ) {
            dismiss()
        }
    }

}
