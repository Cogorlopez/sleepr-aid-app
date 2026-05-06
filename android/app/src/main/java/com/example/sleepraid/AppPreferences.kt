package com.example.sleepraid

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    val noiseType: Flow<NoiseGenerator.NoiseType> = context.dataStore.data.map { prefs ->
        when (prefs[NOISE_TYPE]) {
            "WHITE" -> NoiseGenerator.NoiseType.WHITE
            "BROWN" -> NoiseGenerator.NoiseType.BROWN
            else -> NoiseGenerator.NoiseType.PINK
        }
    }

    val timerHours: Flow<Int> = context.dataStore.data.map { it[TIMER_HOURS] ?: 0 }
    val timerMinutes: Flow<Int> = context.dataStore.data.map { it[TIMER_MINUTES] ?: 30 }

    suspend fun saveNoiseType(type: NoiseGenerator.NoiseType) {
        context.dataStore.edit { it[NOISE_TYPE] = type.name }
    }

    suspend fun saveTimerDuration(hours: Int, minutes: Int) {
        context.dataStore.edit {
            it[TIMER_HOURS] = hours
            it[TIMER_MINUTES] = minutes
        }
    }
}
