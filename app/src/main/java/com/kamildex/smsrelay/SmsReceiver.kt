package com.kamildex.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isActive(context)) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach { sms ->
            val sender = sms.displayOriginatingAddress ?: "Unknown"
            val body = sms.messageBody ?: ""
            if (shouldForward(context, sender, body)) {
                forwardToTelegram(context, sender, body)
            }
        }
    }

    private fun shouldForward(context: Context, sender: String, body: String): Boolean {
        // Forward All mode
        if (Prefs.isForwardAll(context)) return true

        val keywords = Prefs.getKeywords(context)
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val senders = Prefs.getSenders(context)
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        // If both empty — forward all
        if (keywords.isEmpty() && senders.isEmpty()) return true

        return senders.any { sender.lowercase().contains(it) } ||
               keywords.any { body.lowercase().contains(it) }
    }

    private fun forwardToTelegram(context: Context, sender: String, body: String) {
        val token = Prefs.getBotToken(context)
        val chatId = Prefs.getChatId(context)
        if (token.isEmpty() || chatId.isEmpty()) return

        val otpRegex = Regex("\\b\\d{4,8}\\b")
        val otp = otpRegex.find(body)?.value

        val now = java.util.Date()
        val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())

        val message = buildString {
            appendLine("📱 <b>New SMS Received</b>")
            appendLine()
            appendLine("👤 <b>From:</b> $sender")
            appendLine("💬 <b>Message:</b>")
            appendLine(body)
            appendLine()
            if (otp != null) {
                appendLine("🔐 <b>OTP:</b> <code>$otp</code>")
                appendLine()
            }
            appendLine("🕐 <b>Time:</b> ${timeFormat.format(now)}")
            append("📅 <b>Date:</b> ${dateFormat.format(now)}")
        }

        TelegramSender.send(token, chatId, message) { success ->
            SmsLog.add(context, sender, body, success)
            Log.d("SmsReceiver", "Forward ${if (success) "✅" else "❌"}: $sender")
            context.sendBroadcast(Intent("com.kamildex.smsrelay.SMS_FORWARDED"))
        }
    }
}
