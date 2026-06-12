package com.kamildex.smsrelay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

    private fun buildRequest(token: String, chatId: String, message: String): Request {
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
            put("parse_mode", "HTML")
        }.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(body).build()
    }

    fun send(token: String, chatId: String, message: String, onResult: (Boolean) -> Unit) {
        executor.execute {
            sendWithRetry(token, chatId, message, 2, onResult)
        }
    }

    private fun sendWithRetry(
        token: String, chatId: String, message: String,
        retryCount: Int, onResult: (Boolean) -> Unit
    ) {
        try {
            val response = newClient().newCall(buildRequest(token, chatId, message)).execute()
            val success = response.isSuccessful
            response.close()
            if (!success && retryCount > 0) {
                Thread.sleep(1500)
                sendWithRetry(token, chatId, message, retryCount - 1, onResult)
            } else {
                onResult(success)
            }
        } catch (e: IOException) {
            if (retryCount > 0) {
                Thread.sleep(1500)
                sendWithRetry(token, chatId, message, retryCount - 1, onResult)
            } else {
                onResult(false)
            }
        } catch (e: Exception) {
            onResult(false)
        }
    }
}
