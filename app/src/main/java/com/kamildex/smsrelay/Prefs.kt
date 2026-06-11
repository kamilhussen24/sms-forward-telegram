package com.kamildex.smsrelay

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "sms_relay_prefs"
    private const val KEY_TOKEN = "bot_token"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_SENDERS = "senders"
    private const val KEY_ACTIVE = "is_active"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getBotToken(context: Context) = prefs(context).getString(KEY_TOKEN, "") ?: ""
    fun getChatId(context: Context) = prefs(context).getString(KEY_CHAT_ID, "") ?: ""
    fun getKeywords(context: Context) = prefs(context).getString(KEY_KEYWORDS, "") ?: ""
    fun getSenders(context: Context) = prefs(context).getString(KEY_SENDERS, "") ?: ""
    fun isActive(context: Context) = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun save(context: Context, token: String, chatId: String, keywords: String, senders: String) {
        prefs(context).edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_CHAT_ID, chatId)
            putString(KEY_KEYWORDS, keywords)
            putString(KEY_SENDERS, senders)
            apply()
        }
    }

    fun setActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply()
    }
}
