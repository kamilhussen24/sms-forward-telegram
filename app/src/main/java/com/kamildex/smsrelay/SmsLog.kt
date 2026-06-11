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
    private const val KEY_LOG = "sms_log"
    private const val MAX_ENTRIES = 50

    private fun prefs(context: Context) =
        context.getSharedPreferences("sms_relay_prefs", Context.MODE_PRIVATE)

    fun add(context: Context, sender: String, message: String, forwarded: Boolean) {
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val entry = JSONObject().apply {
            put("sender", sender)
            put("message", message)
            put("time", timeFormat.format(now))
            put("date", dateFormat.format(now))
            put("forwarded", forwarded)
        }

        val existing = getAll(context)
        val array = JSONArray()
        array.put(entry)
        existing.take(MAX_ENTRIES - 1).forEach { array.put(JSONObject().apply {
            put("sender", it.sender)
            put("message", it.message)
            put("time", it.time)
            put("date", it.date)
            put("forwarded", it.forwarded)
        }) }

        prefs(context).edit().putString(KEY_LOG, array.toString()).apply()
    }

    fun getAll(context: Context): List<SmsEntry> {
        val json = prefs(context).getString(KEY_LOG, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<SmsEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(SmsEntry(
                sender = obj.getString("sender"),
                message = obj.getString("message"),
                time = obj.getString("time"),
                date = obj.getString("date"),
                forwarded = obj.getBoolean("forwarded")
            ))
        }
        return list
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LOG).apply()
    }
}
