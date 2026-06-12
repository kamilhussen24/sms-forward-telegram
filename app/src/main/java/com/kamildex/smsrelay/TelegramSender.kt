package com.kamildex.smsrelay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object TelegramSender {

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
        sendWithRetry(token, chatId, message, 2, onResult)
    }

    private fun sendWithRetry(
        token: String, chatId: String, message: String,
        retryCount: Int, onResult: (Boolean) -> Unit
    ) {
        try {
            newClient().newCall(buildRequest(token, chatId, message))
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (retryCount > 0) {
                            Thread.sleep(1000)
                            sendWithRetry(token, chatId, message, retryCount - 1, onResult)
                        } else onResult(false)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val success = response.isSuccessful
                        response.close()
                        if (!success && retryCount > 0) {
                            Thread.sleep(1000)
                            sendWithRetry(token, chatId, message, retryCount - 1, onResult)
                        } else onResult(success)
                    }
                })
        } catch (e: Exception) {
            if (retryCount > 0) {
                try { Thread.sleep(1000) } catch (ie: InterruptedException) {}
                sendWithRetry(token, chatId, message, retryCount - 1, onResult)
            } else onResult(false)
        }
    }
}
