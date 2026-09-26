package com.ferrerkiwi.shortsguard

import android.content.Context

/** Stores the user's local choice to pause or resume Shorts blocking. */
internal object ProtectionPreferences {
    private const val PREFERENCES_NAME = "brainrot_preferences"
    private const val PROTECTION_ENABLED_KEY = "protection_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(PROTECTION_ENABLED_KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PROTECTION_ENABLED_KEY, enabled)
            .apply()
    }
}
