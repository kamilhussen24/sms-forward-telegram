package com.kamildex.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isActive(context)) {
                val serviceIntent = Intent(context, SmsForwarderService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
