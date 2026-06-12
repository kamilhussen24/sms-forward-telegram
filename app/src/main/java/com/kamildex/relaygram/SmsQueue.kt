package com.kamildex.relaygram

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

        // Smart OTP detection — look near OTP keywords first
        val otp = Regex("(?i)(?:otp|code|pin|passcode|password|token|verification)[\\s:=]+([0-9]{4,8})")
            .find(item.body)?.groupValues?.getOrNull(1)
            ?: Regex("(?i)([0-9]{4,8})(?:\\s*(?:is|was|:)\\s*(?:your|the)\\s*(?:otp|code|pin))")
                .find(item.body)?.groupValues?.getOrNull(1)
            ?: run {
                // Fallback: only if message contains OTP-related words
                val hasOtpWord = Regex("(?i)(otp|one.time|verify|verification|code|pin)")
                    .containsMatchIn(item.body)
                if (hasOtpWord) Regex("\\b([0-9]{4,8})\\b").find(item.body)?.groupValues?.getOrNull(1)
                else null
            }
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
