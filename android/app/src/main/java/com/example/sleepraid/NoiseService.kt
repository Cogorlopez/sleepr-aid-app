package com.example.sleepraid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

class NoiseService : Service() {

    inner class NoiseBinder : Binder() {
        fun getService(): NoiseService = this@NoiseService
    }

    private val binder = NoiseBinder()
    private val noiseGenerator = NoiseGenerator()

    var isPlaying by mutableStateOf(false)
        private set
    var noiseType by mutableStateOf(NoiseGenerator.NoiseType.PINK)
        private set

    companion object {
        const val ACTION_TOGGLE = "com.example.sleepraid.ACTION_TOGGLE"
        const val CHANNEL_ID = "noise_playback"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Noise Playback", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) toggle()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun play(type: NoiseGenerator.NoiseType = noiseType) {
        noiseType = type
        noiseGenerator.start(type)
        isPlaying = true
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun stopPlayback() {
        noiseGenerator.stop()
        isPlaying = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun toggle() {
        if (isPlaying) stopPlayback() else play()
    }

    fun updateNoiseType(type: NoiseGenerator.NoiseType) {
        noiseType = type
        if (isPlaying) {
            noiseGenerator.noiseType = type
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        noiseGenerator.release()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val togglePi = PendingIntent.getService(
            this, 1,
            Intent(this, NoiseService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val noiseLabel = when (noiseType) {
            NoiseGenerator.NoiseType.PINK -> "Pink Noise"
            NoiseGenerator.NoiseType.WHITE -> "White Noise"
            NoiseGenerator.NoiseType.BROWN -> "Brown Noise"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleepr Aid")
            .setContentText(noiseLabel)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notification, if (isPlaying) "Pause" else "Play", togglePi)
            .build()
    }
}
