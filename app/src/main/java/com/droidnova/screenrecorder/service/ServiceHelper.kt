package com.droidnova.screenrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.droidnova.screenrecorder.MainActivity
import com.droidnova.screenrecorder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer


class ServiceHelper(private val service: ScreenRecordingService) {

    fun muxAudioAndVideo(videoFilePath: String, audioFilePath: String, outputFilePath: String) {
        val muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoExtractor = MediaExtractor()
        try {
            Log.e("myTag mux", "Video File Path: $videoFilePath")
            videoExtractor.setDataSource(videoFilePath)
        } catch (e: IOException) {
            Log.e("myTag mux", "Failed to set video data source: $e")
            return
        }

        val audioExtractor = MediaExtractor()
        try {
            audioExtractor.setDataSource(audioFilePath)
        } catch (e: IOException) {
            Log.e("myTag mux", "Failed to set audio data source with FileDescriptor: $e")
            return
        }


        val videoTrackIndex = selectTrack(videoExtractor, "video/")
        val audioTrackIndex = selectTrack(audioExtractor, "audio/")

        videoExtractor.selectTrack(videoTrackIndex)
        audioExtractor.selectTrack(audioTrackIndex)

        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

        Log.e("myTag mux", "Video Format: $videoFormat")
        Log.e("myTag mux", "Audio Format: $audioFormat")

        // Check video and audio MIME types
        val videoMime = videoFormat.getString(MediaFormat.KEY_MIME)
        val audioMime = audioFormat.getString(MediaFormat.KEY_MIME)

        if (videoMime?.startsWith("video/")==false) {
            Log.e("myTag mux", "Unsupported video format: $videoMime")
            return
        }

        if (audioMime?.startsWith("audio/")==false) {
            Log.e("myTag mux", "Unsupported audio format: $audioMime")
            return
        }

        // Add the video and audio tracks to the muxer
        val muxerVideoTrackIndex = muxer.addTrack(videoFormat)
        val muxerAudioTrackIndex = muxer.addTrack(audioFormat)

        // Proceed with the muxing process
        muxer.start()

        muxTrack(muxer, videoExtractor, muxerVideoTrackIndex)
        muxTrack(muxer, audioExtractor, muxerAudioTrackIndex)

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

        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) {
                break  // End of stream
            }

            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }




    fun createVideoFile(): File {
        val videosDir = service.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(videosDir, "screen_recording_${System.currentTimeMillis()}.mp4")
    }

    fun getAudioFilePath(audioType:String): String {
        val videosDir = service.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val audioFile = File(videosDir, "audio_${System.currentTimeMillis()}.$audioType") // Or a different extension like WAV if converted
        return audioFile.absolutePath
    }



    suspend fun encodePcmToAac(pcmFilePath: String, outputAacFilePath: String) = withContext(
        Dispatchers.IO) {
        try {
            val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)  // 44.1 kHz, Stereo
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)  // 128 kbps
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val pcmFile = File(pcmFilePath)
            val outputFile = File(outputAacFilePath)

            FileInputStream(pcmFile).use { pcmInputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(4096)  // Adjust buffer size if necessary
                    val bufferInfo = MediaCodec.BufferInfo()
                    var isEOS = false

                    Log.d("AAC_ENCODING", "Encoding started for PCM file: $pcmFilePath")

                    while (!isEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            val size = pcmInputStream.read(buffer)

                            if (size > 0) {
                                inputBuffer?.clear()
                                inputBuffer?.put(buffer, 0, size)
                                codec.queueInputBuffer(inputBufferIndex, 0, size, 0, 0)
                                Log.d("AAC_ENCODING", "Read $size bytes from PCM file.")
                            } else {
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            }
                        }

                        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        while (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            val outData = ByteArray(bufferInfo.size)
                            outputBuffer?.let {
                                it.get(outData)
                                // Write ADTS header and data
                                val adtsData = addAdtsHeader(outData, bufferInfo.size)
                                outputStream.write(adtsData)
                                Log.d("AAC_ENCODING", "Wrote ${adtsData.size} bytes to AAC file with ADTS header.")
                            }

                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        }
                    }

                    // Finish up encoding
                    codec.stop()
                    codec.release()

                    Log.d("AAC_ENCODING", "Encoding finished. AAC file size: ${outputFile.length()} bytes")

                    if (outputFile.length() > 0) {
                        Log.d("AAC_ENCODING", "AAC file successfully created: $outputAacFilePath")
                    } else {
                        Log.e("AAC_ENCODING", "AAC file is empty, encoding might have failed.")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("AAC_ENCODING", "Error during encoding: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun addAdtsHeader(aacData: ByteArray, dataSize: Int): ByteArray {
        val adtsHeaderSize = 7
        val adtsData = ByteArray(adtsHeaderSize + dataSize)
        var frameSize = dataSize + adtsHeaderSize

        // ADTS Header fields
        val audioObjectType = 2 // AAC LC
        val samplingFrequencyIndex = 4 // 44.1 kHz
        val channelConfig = 2 // Stereo

        // ADTS Header
        adtsData[0] = 0xFF.toByte() // Syncword (12 bits)
        adtsData[1] = 0xF9.toByte() // Syncword + MPEG version (11 bits) + Layer (2 bits) + Protection absent (1 bit)
        adtsData[2] = ((audioObjectType - 1) shl 6 or (samplingFrequencyIndex shl 2) or (channelConfig shr 2)).toByte()
        adtsData[3] = ((channelConfig and 0x03 shl 6) or (frameSize shr 11)).toByte()
        adtsData[4] = (frameSize shr 3).toByte()
        adtsData[5] = (((frameSize and 0x07) shl 5) or 0x1F).toByte() // Add padding and private bit
        adtsData[6] = 0xFC.toByte() // CRC (16 bits, here fixed to 0xFC)

        // Copy the AAC data
        System.arraycopy(aacData, 0, adtsData, adtsHeaderSize, dataSize)

        return adtsData
    }


    // Create the notification for the foreground service
    fun createNotification(): Notification {
        val openHomeIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Conditionally set FLAG_IMMUTABLE based on the SDK version
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openHomePendingIntent = PendingIntent.getActivity(service, 0, openHomeIntent, flags)

        return NotificationCompat.Builder(service, ScreenRecordingService.FOREGROUND_CHANNEL_ID)
            .setContentTitle(service.getString(R.string.screen_recording_title))  // Use string resources
            .setContentText(service.getString(R.string.screen_recording_text))    // Use string resources
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)  // Ensure the notification cannot be dismissed while recording
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // Use higher priority for visibility
            .setContentIntent(openHomePendingIntent)
            .build()
    }

    // Create the notification channel (only required for Android O and above)
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = service.getString(R.string.screen_recording_channel_name)
            val description = service.getString(R.string.screen_recording_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            // Get NotificationManager and create channel if it doesn't exist
            val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(ScreenRecordingService.FOREGROUND_CHANNEL_ID)

            if (channel == null) {
                NotificationChannel(ScreenRecordingService.FOREGROUND_CHANNEL_ID, name, importance).apply {
                    this.description = description
                    notificationManager.createNotificationChannel(this)
                }
            }
        }
    }
}
