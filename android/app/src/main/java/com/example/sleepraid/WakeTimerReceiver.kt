package com.example.sleepraid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class WakeTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NoiseService.ACTION_WAKE_ALARM -> {
                val serviceIntent = Intent(context, NoiseService::class.java).apply {
                    action = NoiseService.ACTION_WAKE_AUDIO
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            NoiseService.ACTION_CANCEL_WAKE_ALARM -> {
                val serviceIntent = Intent(context, NoiseService::class.java).apply {
                    action = NoiseService.ACTION_CANCEL_WAKE_TIMER
                }
                context.startService(serviceIntent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Service will handle rescheduling on start if needed, 
                // or we could trigger it here. For simplicity, start service to check.
                val serviceIntent = Intent(context, NoiseService::class.java)
                context.startService(serviceIntent)
            }
        }
    }
}
