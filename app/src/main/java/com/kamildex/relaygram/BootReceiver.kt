package com.kamildex.relaygram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isActive(context)) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SmsForwarderService::class.java)
                )
            } catch (e: Exception) {}
        }
    }
}
