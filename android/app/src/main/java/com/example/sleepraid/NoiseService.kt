package com.example.sleepraid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
    var wakeTimerTargetTime by mutableStateOf<Long?>(null)
        private set
    private var wakeTimerUpdateJob: Job? = null

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
        const val ACTION_PLAY = "com.example.sleepraid.ACTION_PLAY"
        const val ACTION_SET_WAKE_TIMER = "com.example.sleepraid.ACTION_SET_WAKE_TIMER"
        const val ACTION_CANCEL_WAKE_TIMER = "com.example.sleepraid.ACTION_CANCEL_WAKE_TIMER"
        const val ACTION_WAKE_ALARM = "com.example.sleepraid.ACTION_WAKE_ALARM"
        const val ACTION_CANCEL_WAKE_ALARM = "com.example.sleepraid.ACTION_CANCEL_WAKE_ALARM"
        const val CHANNEL_ID = "noise_playback"
        const val NOTIFICATION_ID = 1
        const val WAKE_TIMER_NOTIFICATION_ID = 2
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
            wakeTimerTargetTime = prefs.wakeTimerTargetTime.first()
            // If service started (e.g. after reboot), ensure notification is shown if wake timer active
            if (wakeTimerTargetTime != null && !isPlaying) {
                startWakeTimerNotificationUpdates(wakeTimerTargetTime!!)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_PLAY -> play()
            ACTION_SET_WAKE_TIMER -> {
                val h = intent.getIntExtra("hours", 0)
                val m = intent.getIntExtra("minutes", 0)
                scheduleWakeTimer(h, m)
            }
            ACTION_CANCEL_WAKE_TIMER -> cancelWakeTimer()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun play(type: NoiseGenerator.NoiseType = noiseType) {
        if (pauseOtherAudio && !requestAudioFocus()) return
        noiseType = type
        isPlaying = true
        startForeground(NOTIFICATION_ID, buildNotification())
        noiseGenerator.start(type)
        if (wakeTimerTargetTime != null) cancelWakeTimer()
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

    fun scheduleWakeTimer(hours: Int, minutes: Int) {
        val totalMillis = (hours * 3600 + minutes * 60) * 1000L
        if (totalMillis <= 0) return

        val targetTime = System.currentTimeMillis() + totalMillis
        wakeTimerTargetTime = targetTime

        serviceScope.launch {
            prefs.saveWakeTimerTargetTime(targetTime)
            prefs.saveWakeTimerDuration(hours, minutes)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, WakeTimerReceiver::class.java).apply {
            action = ACTION_WAKE_ALARM
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, targetTime, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, targetTime, pi)
        }

        startWakeTimerNotificationUpdates(targetTime)
    }

    fun cancelWakeTimer() {
        wakeTimerTargetTime = null
        wakeTimerUpdateJob?.cancel()
        wakeTimerUpdateJob = null
        serviceScope.launch { prefs.saveWakeTimerTargetTime(null) }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, WakeTimerReceiver::class.java).apply {
            action = ACTION_WAKE_ALARM
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
        )
        pi?.let { alarmManager.cancel(it) }

        getSystemService(NotificationManager::class.java).cancel(WAKE_TIMER_NOTIFICATION_ID)
        if (!isPlaying) stopSelf()
    }

    private fun startWakeTimerNotificationUpdates(targetTime: Long) {
        wakeTimerUpdateJob?.cancel()
        wakeTimerUpdateJob = serviceScope.launch {
            while (isActive && wakeTimerTargetTime != null) {
                showWakeTimerNotification(targetTime)
                delay(1000)
            }
        }
    }

    private fun showWakeTimerNotification(targetTime: Long) {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPi = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, WakeTimerReceiver::class.java).setAction(ACTION_CANCEL_WAKE_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val noiseLabel = when (noiseType) {
            NoiseGenerator.NoiseType.PINK -> "Pink Noise"
            NoiseGenerator.NoiseType.WHITE -> "White Noise"
            NoiseGenerator.NoiseType.BROWN -> "Brown Noise"
        }

        val remainingMillis = targetTime - System.currentTimeMillis()
        val remainingSecs = (remainingMillis / 1000).toInt().coerceAtLeast(0)
        val timeString = formatSeconds(remainingSecs)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleepr Aid")
            .setContentText("$noiseLabel starts in $timeString")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setSilent(true)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, "Cancel", cancelPi)
            .build()

        getSystemService(NotificationManager::class.java).notify(WAKE_TIMER_NOTIFICATION_ID, notification)
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
