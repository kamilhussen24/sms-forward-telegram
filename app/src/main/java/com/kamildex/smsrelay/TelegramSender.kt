package com.kamildex.smsrelay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object TelegramSender {

    private val executor = Executors.newCachedThreadPool()

    // Custom DNS — use Google DNS (8.8.8.8) directly
    // Bypasses Android DNS cache issues
    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                // Try system DNS first
                val result = InetAddress.getAllByName(hostname).toList()
                if (result.isNotEmpty()) result
                else Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private fun newClient() = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .dns(customDns)
        .build()

    fun send(
        token: String,
        chatId: String,
        message: String,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        executor.execute {
            attempt(token.trim(), chatId.trim(), message, retryLeft = 3, onResult = onResult)
        }
    }

    private fun attempt(
        token: String,
        chatId: String,
        message: String,
        retryLeft: Int,
        onResult: (Boolean, String?) -> Unit
    ) {
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
                put("parse_mode", "HTML")
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .addHeader("Connection", "close")
                .post(body)
                .build()

            val response = newClient().newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string() ?: ""
            response.close()

            when {
                code == 200 -> onResult(true, null)

                code == 429 -> {
                    val wait = try {
                        JSONObject(bodyStr).optJSONObject("parameters")
                            ?.optLong("retry_after", 5) ?: 5
                    } catch (e: Exception) { 5L }
                    Thread.sleep(wait * 1000L)
                    if (retryLeft > 0) attempt(token, chatId, message, retryLeft - 1, onResult)
                    else onResult(false, "Too many requests")
                }

                code >= 500 -> {
                    Thread.sleep(3000)
                    if (retryLeft > 0) attempt(token, chatId, message, retryLeft - 1, onResult)
                    else onResult(false, "Telegram server error")
                }

                else -> {
                    val desc = try {
                        JSONObject(bodyStr).optString("description", "Error $code")
                    } catch (e: Exception) { "Error $code" }
                    onResult(false, desc)
                }
            }

        } catch (e: IOException) {
            if (retryLeft > 0) {
                Thread.sleep(2000)
                attempt(token, chatId, message, retryLeft - 1, onResult)
            } else {
                onResult(false, "Network error")
            }
        } catch (e: Exception) {
            onResult(false, e.message ?: "Unknown error")
        }
    }
}
