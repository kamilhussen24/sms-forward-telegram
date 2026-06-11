package com.kamildex.smsrelay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object TelegramSender {

    // Fresh client every time — fixes reconnect issue
    private fun newClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun send(token: String, chatId: String, message: String, onResult: (Boolean) -> Unit) {
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
            put("parse_mode", "HTML")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(body).build()

        newClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
            override fun onResponse(call: Call, response: Response) {
                onResult(response.isSuccessful); response.close()
            }
        })
    }
}
