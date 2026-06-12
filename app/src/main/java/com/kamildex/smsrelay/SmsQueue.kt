package com.kamildex.smsrelay

import android.content.Context
import android.content.Intent
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.text.SimpleDateFormat
import java.util.*

data class SmsItem(val sender: String, val body: String)

object SmsQueue {

    private val queue = LinkedBlockingQueue<SmsItem>()
    private val isRunning = AtomicBoolean(false)

    fun add(item: SmsItem) {
        queue.offer(item)
    }

    fun process(context: Context) {
        if (isRunning.getAndSet(true)) return

        Thread {
            while (queue.isNotEmpty()) {
                val item = queue.poll() ?: break
                sendToTelegram(context.applicationContext, item)
                // Wait between messages to avoid rate limit
                if (queue.isNotEmpty()) Thread.sleep(1500)
            }
            isRunning.set(false)
        }.start()
    }

    private fun sendToTelegram(context: Context, item: SmsItem) {
        val token = Prefs.getBotToken(context)
        val chatId = Prefs.getChatId(context)
        if (token.isEmpty() || chatId.isEmpty()) return

        val otp = Regex("\\b\\d{4,8}\\b").find(item.body)?.value
        val now = Date()
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)

        val msg = buildString {
            appendLine("<b>New SMS</b>")
            appendLine()
            appendLine("<b>From:</b> ${item.sender}")
            appendLine("<b>Message:</b>")
            appendLine(item.body)
            appendLine()
            if (otp != null) {
                appendLine("<b>OTP:</b> <code>$otp</code>")
                appendLine()
            }
            appendLine("<b>Time:</b> $time")
            append("<b>Date:</b> $date")
        }

        TelegramSender.send(token, chatId, msg) { success, _ ->
            SmsLog.add(context, item.sender, item.body, success)
            context.sendBroadcast(Intent("com.kamildex.smsrelay.SMS_FORWARDED"))
        }
    }
}
