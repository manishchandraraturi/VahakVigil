package com.manish.vahakvigil.data.preferences

import android.content.Context
import com.manish.vahakvigil.domain.export.DatasetContributionConsentStore

class DatasetContributionPreferences(context: Context) : DatasetContributionConsentStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    override fun isGranted(): Boolean = preferences.getBoolean(KEY_GRANTED, false)
    override fun grantedAtEpochMillis(): Long? = preferences.getLong(KEY_GRANTED_AT, 0L).takeIf { it > 0L }
    override fun grant(nowEpochMillis: Long) {
        preferences.edit().putBoolean(KEY_GRANTED, true).putLong(KEY_GRANTED_AT, nowEpochMillis).apply()
    }
    override fun withdraw() {
        preferences.edit().putBoolean(KEY_GRANTED, false).remove(KEY_GRANTED_AT).apply()
    }

    private companion object {
        const val PREFERENCES = "dataset_contribution_preferences"
        const val KEY_GRANTED = "contribution_consent_granted"
        const val KEY_GRANTED_AT = "contribution_consent_granted_at"
    }
}
