package com.kamildex.smsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private val queue = LinkedBlockingQueue<Pair<String, String>>()
        private val isProcessing = AtomicBoolean(false)

        fun processQueue(context: Context) {
            if (isProcessing.getAndSet(true)) return
            Thread {
                while (queue.isNotEmpty()) {
                    val (sender, body) = queue.poll() ?: break
                    forwardMessage(context.applicationContext, sender, body)
                    if (queue.isNotEmpty()) Thread.sleep(1500)
                }
                isProcessing.set(false)
            }.start()
        }

        private fun forwardMessage(context: Context, sender: String, body: String) {
            val token = Prefs.getBotToken(context)
            val chatId = Prefs.getChatId(context)
            if (token.isEmpty() || chatId.isEmpty()) return

            val otp = Regex("\\b\\d{4,8}\\b").find(body)?.value
            val now = Date()
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)

            val msg = buildString {
                appendLine("<b>New SMS Received</b>")
                appendLine()
                appendLine("<b>From:</b> $sender")
                appendLine("<b>Message:</b>")
                appendLine(body)
                appendLine()
                if (otp != null) {
                    appendLine("<b>OTP:</b> <code>$otp</code>")
                    appendLine()
                }
                appendLine("<b>Time:</b> $time")
                append("<b>Date:</b> $date")
            }

            TelegramSender.send(token, chatId, msg) { success ->
                SmsLog.add(context, sender, body, success)
                context.sendBroadcast(Intent("com.kamildex.smsrelay.SMS_FORWARDED"))
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isActive(context)) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { sms ->
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.messageBody ?: ""
                if (shouldForward(context, sender, body)) {
                    queue.offer(Pair(sender, body))
                }
            }
            processQueue(context)
        } catch (e: Exception) {}
    }

    private fun shouldForward(c: Context, sender: String, body: String): Boolean {
        if (Prefs.isForwardAll(c)) return true
        val keywords = Prefs.getKeywords(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val senders = Prefs.getSenders(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty() && senders.isEmpty()) return true
        return senders.any { sender.lowercase().contains(it) } ||
               keywords.any { body.lowercase().contains(it) }
    }
}
