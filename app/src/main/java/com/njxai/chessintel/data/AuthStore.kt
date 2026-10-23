package com.njxai.chessintel.data

import android.content.Context

/** Persists the JWT and the signed-in email across app restarts. */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("chessintel_auth", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var email: String?
        get() = prefs.getString("email", null)
        set(value) = prefs.edit().putString("email", value).apply()

    fun clear() = prefs.edit().clear().apply()
}
