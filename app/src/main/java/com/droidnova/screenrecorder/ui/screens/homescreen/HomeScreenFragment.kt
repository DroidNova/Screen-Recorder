package com.droidnova.screenrecorder.ui.screens.homescreen

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.droidnova.screenrecorder.databinding.FragmentHomeScreenBinding
import com.droidnova.screenrecorder.service.ScreenRecordingService
import com.droidnova.screenrecorder.service.ServiceLauncher
import com.droidnova.screenrecorder.ui.bottom_sheets.PermissionBottomSheetFragment
import com.droidnova.screenrecorder.utils.DialogUtil
import com.droidnova.screenrecorder.utils.PreferenceUtil
import com.droidnova.screenrecorder.utils.StorageUtils
import kotlinx.coroutines.launch

class HomeScreenFragment : Fragment() {
    private  var  binding: FragmentHomeScreenBinding? = null

    private var screenRecordingService: ScreenRecordingService? = null
    private var isBound = false
    private var isServiceRunning = MutableLiveData<Boolean>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as ScreenRecordingService.LocalBinder
            screenRecordingService = serviceBinder.getService()
            screenRecordingService?.registerCallback(screenRecordingCallback)
            isBound = true
            if (screenRecordingService?.isServiceRunning == true) {
                binding?.btnStartRecording?.text = "Stop"
            }
            Log.e("myTag","onServiceConnected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenRecordingService?.unregisterCallback()
            screenRecordingService = null
            isBound = false
            Log.e("myTag","onServiceDisconnected")
        }
    }


    private val startScreenCaptureResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val resultCode = result.resultCode
            val data = result.data
            Log.e(ScreenRecordingService.TAG,"resultCode $resultCode , data $data")
            context?.let { ServiceLauncher.startScreenRecordingService(it,this@HomeScreenFragment, resultCode, data) }
            binding?.btnStartRecording?.text = "Stop"
        } else {
            // Handle the case where permission is denied or result is invalid
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentHomeScreenBinding.inflate(inflater,container,false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupButtons()
        setupObservers()
    }

    private fun setupObservers() {
         isServiceRunning.observe(viewLifecycleOwner) { isRunning ->
             binding?.btnStartRecording?.text = if (isRunning) "Stop" else "Start"
         }
    }

    private fun setupUI() {
        context?.let { updateStorageInfo(it) }
        onUpdatePreference(true,true,true)
    }

    private fun setupButtons() {
        binding?.btnStartRecording?.setOnClickListener {
            if (binding?.btnStartRecording?.text.toString()=="Start"){
                context?.let {
                    if (ServiceLauncher.areAllPermissionsGranted(it)){
                        requestScreenRecordingPermission()
                    }else{
                        val permissionBottomSheet = PermissionBottomSheetFragment()
                        permissionBottomSheet.show(childFragmentManager, permissionBottomSheet.tag)
                    }
                }

            }else{
                context?.let { ServiceLauncher.stopService(it) }
            }
        }

        binding?.btnQuality?.setOnClickListener {
            DialogUtil.showQualitySelectorDialog(requireContext(), PreferenceUtil.selectedVideoQuality) { selectedQuality ->
                // Save the selected quality to preferences
                PreferenceUtil.selectedVideoQuality = selectedQuality
                // Update the UI with the new quality
                onUpdatePreference(quality = true, fps = false, resolution = false)
            }
        }

        binding?.btnResolution?.setOnClickListener {
            DialogUtil.showResolutionSelectorDialog(requireContext(), PreferenceUtil.selectedVideoResolution) { selectedResolution ->
                // Save the selected resolution to preferences
                PreferenceUtil.selectedVideoResolution = selectedResolution
                // Update the UI with the new resolution
                onUpdatePreference(quality = false, fps = false, resolution = true)
            }
        }

        binding?.btnFps?.setOnClickListener {
            DialogUtil.showFpsSelectorDialog(requireContext(), PreferenceUtil.selectedVideoFps) { selectedFps ->
                // Save the selected FPS to preferences
                PreferenceUtil.selectedVideoFps = selectedFps
                // Update the UI with the new FPS
                onUpdatePreference(quality = false, fps = true, resolution = false)
            }
        }



    }

    private fun updateStorageInfo(context: Context) {
        // Get available and total storage in GB
        val (availableSpaceGB, totalSpaceGB) = StorageUtils.getStorageInfo(context)

        // Calculate used storage in GB and percentage
        val usedSpaceGB = totalSpaceGB - availableSpaceGB
        val usedStoragePercentage = ((usedSpaceGB / totalSpaceGB) * 100).toInt()

        // Set maximum and progress for the CircularProgressIndicator
        binding?.progressStorage?.max = 100  // Set max to 100 for percentage
        binding?.progressStorage?.progress = usedStoragePercentage

        // Update the TextView with available and total storage in GB
        binding?.tvStorageDetail?.text = "${String.format("%.2f", availableSpaceGB)}\nFree"
    }

    private fun onUpdatePreference(fps: Boolean, quality: Boolean, resolution: Boolean) {
        // Quality
        if (quality) {
            binding?.btnQuality?.text = PreferenceUtil.selectedVideoQuality
        }

        // Resolution
        if (resolution) {
            binding?.btnResolution?.text = PreferenceUtil.selectedVideoResolution
        }

        // FPS
        if (fps) {
            binding?.btnFps?.text = PreferenceUtil.selectedVideoFps
        }
    }



    private fun requestScreenRecordingPermission() {
        val mediaProjectionManager = context?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
        startScreenCaptureResult.launch(permissionIntent)
    }

    private fun bindService() {
        val serviceIntent = Intent(context, ScreenRecordingService::class.java)
        context?.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        serviceConnection?.let {
            context?.unbindService(it)
        }
    }

    override fun onStart() {
        super.onStart()
        bindService() // Bind the service when the fragment is started
    }

    override fun onStop() {
        super.onStop()
        unbindService() // Unbind the service when the fragment is stopped
    }


    private val screenRecordingCallback = object : ScreenRecordingService.RecordingCallbackInterface {
        override fun onRecordingStarted() {
            Log.e("myTag","onRecordingStarted")
        }

        override fun onRecordingStopped() {
            Log.e("myTag","onRecordingStopped")
            binding?.btnStartRecording?.text = "Start"
        }

        override fun onRecordingTimeUpdate(timeString: String) {
            binding?.tvRecordingTime?.text = timeString
        }

    }

}