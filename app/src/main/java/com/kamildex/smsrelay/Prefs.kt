package com.kamildex.smsrelay

import android.content.Context

object Prefs {
    private const val NAME = "sms_relay_prefs"

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getBotToken(c: Context) = p(c).getString("token", "") ?: ""
    fun getChatId(c: Context) = p(c).getString("chat_id", "") ?: ""
    fun getKeywords(c: Context) = p(c).getString("keywords", "") ?: ""
    fun getSenders(c: Context) = p(c).getString("senders", "") ?: ""
    fun isActive(c: Context) = p(c).getBoolean("active", false)
    fun isForwardAll(c: Context) = p(c).getBoolean("forward_all", false)

    fun save(c: Context, token: String, chatId: String, keywords: String, senders: String) {
        p(c).edit().apply {
            putString("token", token)
            putString("chat_id", chatId)
            putString("keywords", keywords)
            putString("senders", senders)
            apply()
        }
    }

    fun setActive(c: Context, active: Boolean) = p(c).edit().putBoolean("active", active).apply()
    fun setForwardAll(c: Context, all: Boolean) = p(c).edit().putBoolean("forward_all", all).apply()
}
