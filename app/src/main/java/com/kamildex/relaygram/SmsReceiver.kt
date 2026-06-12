package com.kamildex.relaygram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isActive(context)) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { sms ->
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.messageBody ?: return@forEach
                if (shouldForward(context, sender, body)) {
                    SmsQueue.add(SmsItem(sender, body))
                }
            }
            SmsQueue.process(context)
        } catch (e: Exception) {}
    }

    private fun shouldForward(c: Context, sender: String, body: String): Boolean {
        if (Prefs.isForwardAll(c)) return true

        val keywords = Prefs.getKeywords(c)
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val senders = Prefs.getSenders(c)
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        // No filters = forward all
        if (keywords.isEmpty() && senders.isEmpty()) return true

        return senders.any { sender.lowercase().contains(it) } ||
               keywords.any { body.lowercase().contains(it) }
    }
}
