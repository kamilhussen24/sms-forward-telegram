package com.kamildex.relaygram

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SmsForwarderService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_relay_ch"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "STOP"
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setActive(this, false)
            stopNetworkMonitor()
            stopForeground(true)
            stopSelf()
            sendBroadcast(Intent("com.kamildex.smsrelay.STATUS_CHANGED"))
            return START_NOT_STICKY
        }

        Prefs.setActive(this, true)
        startForeground(NOTIF_ID, buildNotif(isOnline()))
        startNetworkMonitor()
        sendBroadcast(Intent("com.kamildex.smsrelay.STATUS_CHANGED"))
        return START_STICKY
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(net)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) { false }
    }

    private fun startNetworkMonitor() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNotif(true)
                    sendBroadcast(
                        Intent("com.kamildex.smsrelay.NETWORK_CHANGED")
                            .putExtra("online", true)
                    )
                }
                override fun onLost(network: Network) {
                    updateNotif(false)
                    sendBroadcast(
                        Intent("com.kamildex.smsrelay.NETWORK_CHANGED")
                            .putExtra("online", false)
                    )
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback!!
            )
        } catch (e: Exception) {}
    }

    private fun stopNetworkMonitor() {
        try {
            networkCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
            networkCallback = null
        } catch (e: Exception) {}
    }

    private fun updateNotif(online: Boolean) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, buildNotif(online))
        } catch (e: Exception) {}
    }

    private fun buildNotif(online: Boolean): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, SmsForwarderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (online) "SMS Relay Active" else "No Network")
            .setContentText(if (online) "Forwarding messages to Telegram" else "Waiting for network...")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "SMS Relay", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
                .also {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .createNotificationChannel(it)
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNetworkMonitor()
        Prefs.setActive(this, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
