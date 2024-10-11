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
import com.droidnova.screenrecorder.databinding.FragmentHomeScreenBinding
import com.droidnova.screenrecorder.service.ScreenRecordingService
import com.droidnova.screenrecorder.service.ServiceLauncher

class HomeScreenFragment : Fragment() {
    private  var  binding: FragmentHomeScreenBinding? = null

    private var screenRecordingService: ScreenRecordingService? = null
    private var isBound = false

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
            context?.let { ServiceLauncher.startScreenRecordingService(it, resultCode, data!!) }
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
        setupButtons()
    }

    private fun setupButtons() {
        binding?.btnStartRecording?.setOnClickListener {
            if (binding?.btnStartRecording?.text.toString()=="Start"){
                requestScreenRecordingPermission()
            }else{
                context?.let { ServiceLauncher.stopService(it) }
                binding?.btnStartRecording?.text = "Start"
            }
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
        }

    }

}