package com.kamildex.smsrelay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object TelegramSender {

    private val executor = Executors.newCachedThreadPool()

    private fun newClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build()

    fun send(token: String, chatId: String, message: String, onResult: (Boolean, String?) -> Unit) {
        executor.execute {
            sendInternal(token, chatId, message, 2, onResult)
        }
    }

    private fun sendInternal(
        token: String, chatId: String, message: String,
        retryCount: Int, onResult: (Boolean, String?) -> Unit
    ) {
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
                put("parse_mode", "HTML")
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(body).build()

            val response = newClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val success = response.isSuccessful
            response.close()

            if (!success) {
                // Parse Telegram error
                val errorDesc = try {
                    JSONObject(responseBody).optString("description", "Unknown error")
                } catch (e: JSONException) { "Unknown error" }

                // Rate limit — wait and retry
                if (response.code == 429 && retryCount > 0) {
                    val retryAfter = try {
                        JSONObject(responseBody)
                            .optJSONObject("parameters")
                            ?.optLong("retry_after", 3) ?: 3
                    } catch (e: Exception) { 3L }
                    Thread.sleep(retryAfter * 1000)
                    sendInternal(token, chatId, message, retryCount - 1, onResult)
                    return
                }

                // Other error — retry once
                if (retryCount > 0) {
                    Thread.sleep(2000)
                    sendInternal(token, chatId, message, retryCount - 1, onResult)
                    return
                }

                onResult(false, errorDesc)
            } else {
                onResult(true, null)
            }

        } catch (e: IOException) {
            if (retryCount > 0) {
                Thread.sleep(2000)
                sendInternal(token, chatId, message, retryCount - 1, onResult)
            } else {
                onResult(false, "Network error: ${e.message}")
            }
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }
}
