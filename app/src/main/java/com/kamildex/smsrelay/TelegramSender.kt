package com.kamildex.smsrelay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object TelegramSender {
    private val client = OkHttpClient()

    fun send(token: String, chatId: String, message: String, onResult: (Boolean) -> Unit) {
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
            put("parse_mode", "HTML")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onResult(false) }
            override fun onResponse(call: Call, response: Response) {
                onResult(response.isSuccessful); response.close()
            }
        })
    }
}
