package com.droidnova.screenrecorder.utils

import android.media.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

object EncodingUtil {

    fun startRealTimeEncoding(audioRecord: AudioRecord, outputFilePath: String) {
        Thread {
            try {
                val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
                val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2) // 44.1 kHz, Stereo
                format.setInteger(MediaFormat.KEY_BIT_RATE, 128000) // 128 kbps
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                FileOutputStream(File(outputFilePath)).use { outputStream ->
                    val audioBuffer = ByteArray(4096)
                    val bufferInfo = MediaCodec.BufferInfo()

                    while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                        if (read > 0) {
                            val inputBufferIndex = codec.dequeueInputBuffer(10000)
                            if (inputBufferIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                                inputBuffer?.clear()
                                inputBuffer?.put(audioBuffer, 0, read)
                                codec.queueInputBuffer(inputBufferIndex, 0, read, System.nanoTime() / 1000, 0)
                            }
                        }

                        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        while (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            val outData = ByteArray(bufferInfo.size)
                            outputBuffer?.get(outData)

                            // Write AAC data with ADTS header to the file
                            val adtsData = addAdtsHeader(outData, bufferInfo.size)
                            outputStream.write(adtsData)

                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        }
                    }

                    // End-of-stream handling
                    codec.stop()
                    codec.release()
                    Log.i("AudioEncoderUtil", "Real-time AAC encoding complete")
                }

            } catch (e: Exception) {
                Log.e("AudioEncoderUtil", "Error in real-time encoding: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    fun startRealTimeMuxing(audioRecord: AudioRecord, videoPath: String, outputFilePath: String) {
        Thread {
            try {
                val muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
                val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2)
                audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

                codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                val videoExtractor = MediaExtractor()
                videoExtractor.setDataSource(videoPath)

                var videoTrackIndex = -1
                var audioTrackIndex = -1
                var isAudioTrackAdded = false
                var isVideoTrackAdded = false

                // Adding video track first
                for (i in 0 until videoExtractor.trackCount) {
                    val format = videoExtractor.getTrackFormat(i)
                    if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        videoExtractor.selectTrack(i)
                        videoTrackIndex = muxer.addTrack(format)
                        isVideoTrackAdded = true
                        break
                    }
                }

                // Ensure video track is present
                if (!isVideoTrackAdded) {
                    Log.e("MuxingUtil", "No video track found")
                    return@Thread
                }

                // Start the muxer once video track is added
                muxer.start()

                val bufferInfo = MediaCodec.BufferInfo()
                val audioBuffer = ByteArray(4096)
                val videoBuffer = ByteBuffer.allocate(1024 * 1024)

                // Start real-time encoding and muxing
                while (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                    if (read > 0) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            inputBuffer?.clear()
                            inputBuffer?.put(audioBuffer, 0, read)
                            codec.queueInputBuffer(inputBufferIndex, 0, read, System.nanoTime() / 1000, 0)
                        }
                    }

                    var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        val outData = ByteArray(bufferInfo.size)
                        outputBuffer?.get(outData)

                        if (!isAudioTrackAdded) {
                            val audioFormatEncoded = codec.outputFormat
                            audioTrackIndex = muxer.addTrack(audioFormatEncoded)
                            isAudioTrackAdded = true
                        }

                        if (bufferInfo.size > 0 && audioTrackIndex >= 0) {
                            bufferInfo.presentationTimeUs = System.nanoTime() / 1000
                            muxer.writeSampleData(audioTrackIndex, outputBuffer!!, bufferInfo)
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    }

                    // Mux video data
                    val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                    if (sampleSize > 0) {
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        bufferInfo.flags = videoExtractor.sampleFlags

                        muxer.writeSampleData(videoTrackIndex, videoBuffer, bufferInfo)
                        videoExtractor.advance()
                    } else {
                        // End of video stream
                        break
                    }
                }

                // Stop and release resources
                codec.stop()
                codec.release()
                muxer.stop()
                muxer.release()
                videoExtractor.release()

                Log.i("MuxingUtil", "Real-time muxing complete")

            } catch (e: Exception) {
                Log.e("MuxingUtil", "Error during real-time muxing: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    suspend fun encodePcmToAac(pcmFilePath: String, outputAacFilePath: String) = withContext(Dispatchers.IO) {
        try {
            val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2) // 44.1 kHz, Stereo
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000) // 128 kbps
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val pcmFile = File(pcmFilePath)
            val outputFile = File(outputAacFilePath)

            FileInputStream(pcmFile).use { pcmInputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(4096)
                    val bufferInfo = MediaCodec.BufferInfo()
                    var isEOS = false

                    while (!isEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            val size = pcmInputStream.read(buffer)

                            if (size > 0) {
                                inputBuffer?.clear()
                                inputBuffer?.put(buffer, 0, size)
                                codec.queueInputBuffer(inputBufferIndex, 0, size, 0, 0)
                            } else {
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            }
                        }

                        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        while (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            val outData = ByteArray(bufferInfo.size)
                            outputBuffer?.get(outData)

                            val adtsData = addAdtsHeader(outData, bufferInfo.size)
                            outputStream.write(adtsData)

                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        }
                    }
                    codec.stop()
                    codec.release()
                }
            }
        } catch (e: Exception) {
            Log.e("EncodingUtil", "Error encoding: ${e.message}")
            e.printStackTrace()
        }
    }

    // Helper function to add ADTS headers to AAC frames
    fun addAdtsHeader(aacData: ByteArray, dataSize: Int): ByteArray {
        val adtsHeaderSize = 7
        val adtsData = ByteArray(adtsHeaderSize + dataSize)
        val frameSize = dataSize + adtsHeaderSize

        val audioObjectType = 2 // AAC LC
        val samplingFrequencyIndex = 4 // 44.1 kHz
        val channelConfig = 2 // Stereo

        adtsData[0] = 0xFF.toByte()
        adtsData[1] = 0xF9.toByte()
        adtsData[2] = ((audioObjectType - 1) shl 6 or (samplingFrequencyIndex shl 2) or (channelConfig shr 2)).toByte()
        adtsData[3] = ((channelConfig and 0x03 shl 6) or (frameSize shr 11)).toByte()
        adtsData[4] = (frameSize shr 3).toByte()
        adtsData[5] = (((frameSize and 0x07) shl 5) or 0x1F).toByte()
        adtsData[6] = 0xFC.toByte()

        System.arraycopy(aacData, 0, adtsData, adtsHeaderSize, dataSize)

        return adtsData
    }
}
