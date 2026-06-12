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
    fun isConfigSaved(c: Context) = p(c).getBoolean("config_saved", false)

    fun saveConfig(c: Context, token: String, chatId: String) {
        p(c).edit().apply {
            putString("token", token)
            putString("chat_id", chatId)
            putBoolean("config_saved", true)
            apply()
        }
    }

    fun saveFilters(c: Context, keywords: String, senders: String) {
        p(c).edit().apply {
            putString("keywords", keywords)
            putString("senders", senders)
            apply()
        }
    }

    fun save(c: Context, token: String, chatId: String, keywords: String, senders: String) {
        p(c).edit().apply {
            putString("token", token)
            putString("chat_id", chatId)
            putString("keywords", keywords)
            putString("senders", senders)
            putBoolean("config_saved", true)
            apply()
        }
    }

    fun setActive(c: Context, v: Boolean) = p(c).edit().putBoolean("active", v).apply()
    fun setForwardAll(c: Context, v: Boolean) = p(c).edit().putBoolean("forward_all", v).apply()
}
