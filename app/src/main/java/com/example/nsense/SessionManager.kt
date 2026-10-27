package com.example.nsense

import android.content.Context

object SessionManager {

    private const val PREFS = "nsense_session"

    fun login(context: Context, userId: Int, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("user_id", userId)
            .putString("user_name", name)
            .apply()
    }

    fun userId(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("user_id", -1)

    fun userName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("user_name", "") ?: ""

    fun isLoggedIn(context: Context): Boolean = userId(context) != -1

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        LastReading.clear(context)
    }
}