package com.example.sleepraid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NoiseService : Service() {

    inner class NoiseBinder : Binder() {
        fun getService(): NoiseService = this@NoiseService
    }

    private val binder = NoiseBinder()
    private val noiseGenerator = NoiseGenerator()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: AppPreferences
    private var timerJob: Job? = null

    var isPlaying by mutableStateOf(false)
        private set
    var noiseType by mutableStateOf(NoiseGenerator.NoiseType.PINK)
        private set
    var timerActive by mutableStateOf(false)
        private set
    var timerRemainingSeconds by mutableIntStateOf(0)
        private set
    var pauseOtherAudio by mutableStateOf(false)
        private set

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> stopPlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) stopPlayback()
            }
            AudioManager.AUDIOFOCUS_GAIN -> { /* do not auto-resume */ }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.example.sleepraid.ACTION_TOGGLE"
        const val CHANNEL_ID = "noise_playback"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Noise Playback", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        serviceScope.launch {
            noiseType = prefs.noiseType.first()
            pauseOtherAudio = prefs.pauseOtherAudio.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) toggle()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun play(type: NoiseGenerator.NoiseType = noiseType) {
        if (pauseOtherAudio && !requestAudioFocus()) return
        noiseType = type
        noiseGenerator.start(type)
        isPlaying = true
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun stopPlayback() {
        cancelTimer()
        noiseGenerator.stop()
        isPlaying = false
        if (pauseOtherAudio) abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun toggle() {
        if (isPlaying) stopPlayback() else play()
    }

    fun updatePauseOtherAudio(value: Boolean) {
        pauseOtherAudio = value
        serviceScope.launch { prefs.savePauseOtherAudio(value) }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    fun updateNoiseType(type: NoiseGenerator.NoiseType) {
        noiseType = type
        serviceScope.launch { prefs.saveNoiseType(type) }
        if (isPlaying) {
            noiseGenerator.noiseType = type
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    fun startTimer(hours: Int, minutes: Int) {
        timerJob?.cancel()
        val total = hours * 3600 + minutes * 60
        if (total <= 0) return
        timerRemainingSeconds = total
        timerActive = true
        if (isPlaying) refreshNotification()
        timerJob = serviceScope.launch {
            while (isActive && timerRemainingSeconds > 0) {
                delay(1000L)
                timerRemainingSeconds--
                if (isPlaying && (timerRemainingSeconds % 60 == 0 || timerRemainingSeconds < 60)) {
                    refreshNotification()
                }
            }
            if (isActive) onTimerExpired()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        timerActive = false
        timerRemainingSeconds = 0
    }

    private fun onTimerExpired() {
        timerActive = false
        timerJob = null
        noiseGenerator.stop()
        isPlaying = false
        if (pauseOtherAudio) abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        noiseGenerator.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
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
        val contentText = if (timerActive) {
            "$noiseLabel · ${formatSeconds(timerRemainingSeconds)} left"
        } else {
            noiseLabel
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleepr Aid")
            .setContentText(contentText)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notification, if (isPlaying) "Pause" else "Play", togglePi)
            .build()
    }
}

private fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
