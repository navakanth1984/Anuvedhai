package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    var isRecording = false
        private set

    fun startRecording(): File? {
        if (isRecording) return currentFile

        try {
            val cacheFile = File(context.cacheDir, "record_${System.currentTimeMillis()}.m4a")
            currentFile = cacheFile

            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(cacheFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            return cacheFile
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            currentFile = null
            mediaRecorder = null
            isRecording = false
            return null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording cleanly", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return currentFile
    }

    fun cancelRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to cancel recording", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            currentFile?.delete()
            currentFile = null
        }
    }
}
