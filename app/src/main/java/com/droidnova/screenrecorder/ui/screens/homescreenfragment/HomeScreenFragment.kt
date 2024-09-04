package com.droidnova.screenrecorder.ui.screens.homescreenfragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.databinding.FragmentHomeScreenBinding

class HomeScreenFragment : Fragment() {
 private  var  binding: FragmentHomeScreenBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentHomeScreenBinding.inflate(inflater,container,false)
        return binding?.root
    }
}