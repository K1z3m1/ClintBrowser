package com.jhaiian.clint.tabs

import android.content.SharedPreferences

/**
 * Low RAM mode: only the tab being viewed keeps a loaded page. Every other tab stays in the tab list
 * but its page is unloaded (state saved) and is restored the moment the tab is opened again.
 */
object LowRamMode {
    const val PREF_LOW_RAM_MODE = "low_ram_mode"

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_LOW_RAM_MODE, false)
}
