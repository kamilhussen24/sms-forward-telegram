package com.kamildex.smsrelay

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsForwarderService : Service() {
    companion object {
        const val CHANNEL_ID = "sms_relay_ch"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "STOP"
    }

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setActive(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            sendBroadcast(Intent("com.kamildex.smsrelay.STATUS_CHANGED"))
        } else {
            Prefs.setActive(this, true)
            startForeground(NOTIF_ID, buildNotif())
            sendBroadcast(Intent("com.kamildex.smsrelay.STATUS_CHANGED"))
        }
        return START_STICKY
    }

    private fun buildNotif(): Notification {
        val stopPi = PendingIntent.getService(this, 0,
            Intent(this, SmsForwarderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        val openPi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Relay Active")
            .setContentText("Forwarding messages to Telegram")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "SMS Relay", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
                .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
