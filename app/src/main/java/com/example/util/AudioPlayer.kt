package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentVolume = 1.0f
    private var currentSpeed = 1.0f
    var isPlaying = false
        private set

    fun setVolume(volume: Float) {
        currentVolume = volume
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error setting volume on MediaPlayer", e)
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        try {
            mediaPlayer?.let { mp ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error setting speed on MediaPlayer", e)
        }
    }

    fun playFile(file: File, playbackSpeed: Float = currentSpeed, onFinished: (() -> Unit)? = null) {
        stopPlaying()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setVolume(currentVolume, currentVolume)
                prepare()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        val params = playbackParams
                        params.speed = playbackSpeed
                        playbackParams = params
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Failed to set playbackParams speed", e)
                    }
                }
                setOnCompletionListener {
                    this@AudioPlayer.isPlaying = false
                    onFinished?.invoke()
                    releasePlayer()
                }
                start()
            }
            this@AudioPlayer.isPlaying = true
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing file: ${file.absolutePath}", e)
            this@AudioPlayer.isPlaying = false
            onFinished?.invoke()
        }
    }

    fun playBase64Audio(base64Data: String, playbackSpeed: Float = currentSpeed, onFinished: (() -> Unit)? = null) {
        stopPlaying()
        try {
            val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "temp_tts_playback.mp3")
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
            }
            playFile(tempFile, playbackSpeed, onFinished)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing Base64 audio", e)
            isPlaying = false
            onFinished?.invoke()
        }
    }

    fun stopPlaying() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping player", e)
        } finally {
            mediaPlayer = null
            isPlaying = false
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
    }
}
