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
            putString("token", token.trim())
            putString("chat_id", chatId.trim())
            putBoolean("config_saved", true)
            apply()
        }
    }

    fun save(c: Context, token: String, chatId: String, keywords: String, senders: String, forwardAll: Boolean? = null) {
        p(c).edit().apply {
            putString("token", token.trim())
            putString("chat_id", chatId.trim())
            putString("keywords", keywords.trim())
            putString("senders", senders.trim())
            putBoolean("config_saved", true)
            if (forwardAll != null) putBoolean("forward_all", forwardAll)
            apply()
        }
    }

    fun setActive(c: Context, v: Boolean) = p(c).edit().putBoolean("active", v).apply()
    fun setForwardAll(c: Context, v: Boolean) = p(c).edit().putBoolean("forward_all", v).apply()
}
