package com.kamildex.smsrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsForwarderService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_relay_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setActive(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                sendBroadcast(Intent("com.kamildex.smsrelay.STATUS_CHANGED"))
            }
            else -> {
                Prefs.setActive(this, true)
                startForeground(NOTIF_ID, buildNotification())
                sendBroadcast(Intent("com.kamildex.smsrelay.STATUS_CHANGED"))
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SmsForwarderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Relay Active")
            .setContentText("Forwarding SMS to Telegram")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS forwarding service"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
