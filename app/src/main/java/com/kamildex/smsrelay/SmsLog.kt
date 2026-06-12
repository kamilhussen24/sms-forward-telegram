package com.kamildex.smsrelay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class SmsEntry(
    val sender: String,
    val message: String,
    val time: String,
    val date: String,
    val forwarded: Boolean
)

object SmsLog {
    private const val MAX = 20
    private fun p(c: Context) = c.getSharedPreferences("sms_relay_prefs", Context.MODE_PRIVATE)

    fun add(c: Context, sender: String, message: String, forwarded: Boolean) {
        val now = Date()
        val entry = JSONObject().apply {
            put("sender", sender); put("message", message)
            put("time", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now))
            put("date", SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now))
            put("forwarded", forwarded)
        }
        val arr = JSONArray().apply { put(entry) }
        getAll(c).take(MAX - 1).forEach { e ->
            arr.put(JSONObject().apply {
                put("sender", e.sender); put("message", e.message)
                put("time", e.time); put("date", e.date); put("forwarded", e.forwarded)
            })
        }
        p(c).edit().putString("log", arr.toString()).apply()
    }

    fun getAll(c: Context): List<SmsEntry> {
        return try {
            val arr = JSONArray(p(c).getString("log", "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { o ->
                    SmsEntry(o.getString("sender"), o.getString("message"),
                        o.getString("time"), o.getString("date"), o.getBoolean("forwarded"))
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun clear(c: Context) = p(c).edit().remove("log").apply()
}
