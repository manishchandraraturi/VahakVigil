package com.manish.vahakvigil.data.preferences

import android.content.Context

/** Small persistent gate for first-launch onboarding. */
class OnboardingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    fun isComplete(): Boolean = preferences.getBoolean(KEY_COMPLETE, false)
    fun markComplete() { preferences.edit().putBoolean(KEY_COMPLETE, true).apply() }

    companion object {
        private const val PREFERENCES = "onboarding_preferences"
        private const val KEY_COMPLETE = "complete"
    }
}
