package com.example.sleepraid

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val NOISE_TYPE = stringPreferencesKey("noise_type")
        private val TIMER_HOURS = intPreferencesKey("timer_hours")
        private val TIMER_MINUTES = intPreferencesKey("timer_minutes")
        private val PAUSE_OTHER_AUDIO = booleanPreferencesKey("pause_other_audio")
        private val USE_WAKE_VOLUME = booleanPreferencesKey("use_wake_volume")
        private val WAKE_VOLUME = floatPreferencesKey("wake_volume")
        private val WAKE_TIMER_HOURS = intPreferencesKey("wake_timer_hours")
        private val WAKE_TIMER_MINUTES = intPreferencesKey("wake_timer_minutes")
        private val WAKE_TIMER_TARGET_TIME = stringPreferencesKey("wake_timer_target_time") // Store Long as String
    }

    val noiseType: Flow<NoiseGenerator.NoiseType> = context.dataStore.data.map { prefs ->
        when (prefs[NOISE_TYPE]) {
            "WHITE" -> NoiseGenerator.NoiseType.WHITE
            "BROWN" -> NoiseGenerator.NoiseType.BROWN
            "GREEN" -> NoiseGenerator.NoiseType.GREEN
            else -> NoiseGenerator.NoiseType.PINK
        }
    }

    val timerHours: Flow<Int> = context.dataStore.data.map { it[TIMER_HOURS] ?: 0 }
    val timerMinutes: Flow<Int> = context.dataStore.data.map { it[TIMER_MINUTES] ?: 30 }

    val wakeTimerHours: Flow<Int> = context.dataStore.data.map { it[WAKE_TIMER_HOURS] ?: 0 }
    val wakeTimerMinutes: Flow<Int> = context.dataStore.data.map { it[WAKE_TIMER_MINUTES] ?: 30 }
    val wakeTimerTargetTime: Flow<Long?> = context.dataStore.data.map { it[WAKE_TIMER_TARGET_TIME]?.toLongOrNull() }

    suspend fun saveNoiseType(type: NoiseGenerator.NoiseType) {
        context.dataStore.edit { it[NOISE_TYPE] = type.name }
    }

    suspend fun saveTimerDuration(hours: Int, minutes: Int) {
        context.dataStore.edit {
            it[TIMER_HOURS] = hours
            it[TIMER_MINUTES] = minutes
        }
    }

    suspend fun saveWakeTimerDuration(hours: Int, minutes: Int) {
        context.dataStore.edit {
            it[WAKE_TIMER_HOURS] = hours
            it[WAKE_TIMER_MINUTES] = minutes
        }
    }

    suspend fun saveWakeTimerTargetTime(targetTime: Long?) {
        context.dataStore.edit {
            if (targetTime == null) {
                it.remove(WAKE_TIMER_TARGET_TIME)
            } else {
                it[WAKE_TIMER_TARGET_TIME] = targetTime.toString()
            }
        }
    }

    val pauseOtherAudio: Flow<Boolean> = context.dataStore.data.map { it[PAUSE_OTHER_AUDIO] ?: false }

    suspend fun savePauseOtherAudio(value: Boolean) {
        context.dataStore.edit { it[PAUSE_OTHER_AUDIO] = value }
    }

    val useWakeVolume: Flow<Boolean> = context.dataStore.data.map { it[USE_WAKE_VOLUME] ?: false }
    val wakeVolume: Flow<Float> = context.dataStore.data.map { it[WAKE_VOLUME] ?: 0.7f }

    suspend fun saveUseWakeVolume(value: Boolean) {
        context.dataStore.edit { it[USE_WAKE_VOLUME] = value }
    }

    suspend fun saveWakeVolume(volume: Float) {
        context.dataStore.edit { it[WAKE_VOLUME] = volume }
    }
}
