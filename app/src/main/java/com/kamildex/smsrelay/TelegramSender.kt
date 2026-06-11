package com.kamildex.smsrelay

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object TelegramSender {

    private val client = OkHttpClient()

    fun send(token: String, chatId: String, message: String, onResult: (Boolean) -> Unit) {
        val url = "https://api.telegram.org/bot$token/sendMessage"

        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
            put("parse_mode", "HTML")
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramSender", "Failed: ${e.message}")
                onResult(false)
            }

            override fun onResponse(call: Call, response: Response) {
                onResult(response.isSuccessful)
                response.close()
            }
        })
    }
}
