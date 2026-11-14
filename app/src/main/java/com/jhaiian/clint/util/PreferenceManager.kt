package com.jhaiian.clint.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Drop-in replacement for com.jhaiian.clint.util.PreferenceManager.
 * Uses the same file name ("<package>_preferences") and mode, so existing user settings are preserved.
 */
object PreferenceManager {
    fun getDefaultSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
}
