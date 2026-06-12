package com.kamildex.relaygram

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object TelegramSender {

    private val executor = Executors.newCachedThreadPool()

    // Cache DNS — faster after first request
    private val dnsCache = mutableMapOf<String, List<InetAddress>>()

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            dnsCache[hostname]?.let { return it }
            return try {
                val result = InetAddress.getAllByName(hostname).toList()
                if (result.isNotEmpty()) {
                    dnsCache[hostname] = result
                    result
                } else Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    // Shared client with connection pool — reuse connections
    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(3, 30, TimeUnit.SECONDS))
        .dns(customDns)
        .build()

    fun send(
        token: String,
        chatId: String,
        message: String,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        val future = executor.submit<Unit> {
            attempt(token.trim(), chatId.trim(), message, retryLeft = 2, onResult = onResult)
        }
        executor.execute {
            try {
                future.get(30, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                onResult(false, "Request timed out. Check your connection.")
            } catch (e: Exception) {
                onResult(false, "Error: ${e.message}")
            }
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
                .post(body)
                .build()

            val response = sharedClient.newCall(request).execute()
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
                    else onResult(false, "Too many requests. Wait and try again.")
                }

                code >= 500 -> {
                    Thread.sleep(2000)
                    if (retryLeft > 0) attempt(token, chatId, message, retryLeft - 1, onResult)
                    else onResult(false, "Telegram server error. Try again later.")
                }

                else -> {
                    val desc = try {
                        JSONObject(bodyStr).optString("description", "Error $code")
                    } catch (e: Exception) { "Error $code" }
                    onResult(false, desc)
                }
            }

        } catch (e: IOException) {
            // Clear DNS cache on network error — force fresh lookup
            dnsCache.clear()
            if (retryLeft > 0) {
                Thread.sleep(1000)
                attempt(token, chatId, message, retryLeft - 1, onResult)
            } else {
                onResult(false, "Network error. Check your connection.")
            }
        } catch (e: Exception) {
            onResult(false, e.message ?: "Unknown error")
        }
    }
}
