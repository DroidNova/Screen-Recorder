package com.droidnova.screenrecorder.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer

object MuxingUtil {
    fun muxAudioAndVideo(videoFilePath: String, audioFilePath: String, outputFilePath: String) {
        val muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        Log.i("MuxingUtil", "Video File Path: $videoFilePath")
        Log.i("MuxingUtil", "Audio File Path: $audioFilePath")

        try {
            videoExtractor.setDataSource(videoFilePath)
            audioExtractor.setDataSource(audioFilePath)
        } catch (e: IOException) {
            Log.e("MuxingUtil", "Failed to set data source: $e")
            return
        }

        val videoTrackIndex = selectTrack(videoExtractor, "video/")
        val audioTrackIndex = selectTrack(audioExtractor, "audio/")

        Log.i("MuxingUtil", "Video track index: $videoTrackIndex")
        Log.i("MuxingUtil", "Audio track index: $audioTrackIndex")

        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

        Log.i("MuxingUtil", "Video format: $videoFormat")
        Log.i("MuxingUtil", "Audio format: $audioFormat")

        val muxerVideoTrackIndex = muxer.addTrack(videoFormat)
        val muxerAudioTrackIndex = muxer.addTrack(audioFormat)

        Log.i("MuxingUtil", "Muxer video track index: $muxerVideoTrackIndex")
        Log.i("MuxingUtil", "Muxer audio track index: $muxerAudioTrackIndex")

        muxer.start()
        Log.i("MuxingUtil", "Muxing started")

        muxTrack(muxer, videoExtractor, muxerVideoTrackIndex)
        muxTrack(muxer, audioExtractor, muxerAudioTrackIndex)

        Log.e("MuxingUtil", "Muxing completed")

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }
    private fun selectTrack(extractor: MediaExtractor, mimeTypePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mimeType = format.getString(MediaFormat.KEY_MIME)
            if (mimeType?.startsWith(mimeTypePrefix) == true) {
                return i
            }
        }
        throw IllegalArgumentException("No track found with mime type: $mimeTypePrefix")
    }

    private fun muxTrack(muxer: MediaMuxer, extractor: MediaExtractor, trackIndex: Int) {
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        extractor.selectTrack(trackIndex) // Select the track
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC) // Seek to the start

        var sampleCount = 0
        while (true) {
            bufferInfo.offset = 0
            val sampleSize = extractor.readSampleData(buffer, 0)

            if (sampleSize < 0) {
                Log.i("MuxingUtil", "End of stream reached")
                break
            }

            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            extractor.advance() // Advance to the next sample
            sampleCount++
        }

        Log.i("MuxingUtil", "Total samples written: $sampleCount")
    }


}
