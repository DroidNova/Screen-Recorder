package com.droidnova.screenrecorder.utils

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer

object RealTimeMuxerUtil {
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var muxerStarted = false

    fun startMuxing(outputFilePath: String) {
        try {
            muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerStarted = false
            Log.i("RealTimeMuxerUtil", "Muxer initialized")
        } catch (e: IOException) {
            Log.e("RealTimeMuxerUtil", "Failed to start muxer: ${e.message}")
        }
    }

    fun addVideoTrack(format: MediaFormat) {
        muxer?.let {
            videoTrackIndex = it.addTrack(format)
            checkStartMuxer()
            Log.i("RealTimeMuxerUtil", "Video track added")
        }
    }

    fun addAudioTrack(format: MediaFormat) {
        muxer?.let {
            audioTrackIndex = it.addTrack(format)
            checkStartMuxer()
            Log.i("RealTimeMuxerUtil", "Audio track added")
        }
    }

    private fun checkStartMuxer() {
        if (videoTrackIndex != -1 && audioTrackIndex != -1 && !muxerStarted) {
            muxer?.start()
            muxerStarted = true
            Log.i("RealTimeMuxerUtil", "Muxer started")
        }
    }

    fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (muxerStarted) {
            muxer?.writeSampleData(trackIndex, byteBuffer, bufferInfo)
        } else {
            Log.e("RealTimeMuxerUtil", "Muxer not started, can't write sample data")
        }
    }

    fun stopMuxing() {
        try {
            if (muxerStarted) {
                muxer?.stop()
                muxer?.release()
                muxerStarted = false
                Log.i("RealTimeMuxerUtil", "Muxer stopped and released")
            } else {
                Log.e("RealTimeMuxerUtil", "Muxer not started or already stopped")
            }
        } catch (e: IllegalStateException) {
            Log.e("RealTimeMuxerUtil", "Error stopping muxer: ${e.message}")
        }
    }
}
