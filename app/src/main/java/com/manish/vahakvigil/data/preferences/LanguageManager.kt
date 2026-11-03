package com.manish.vahakvigil.data.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Persists an explicit app language and applies it through Android resources. */
class LanguageManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun languageTag(): String = preferences.getString(KEY_LANGUAGE, ENGLISH_TAG) ?: ENGLISH_TAG

    fun setLanguageTag(tag: String) {
        require(tag in supportedLanguages.map(LanguageOption::tag)) { "Unsupported language tag: $tag" }
        preferences.edit().putString(KEY_LANGUAGE, tag).apply()
    }

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(languageTag())
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    data class LanguageOption(val tag: String, val label: String)

    companion object {
        const val ENGLISH_TAG = "en"
        const val HINDI_TAG = "hi"
        val supportedLanguages = listOf(
            LanguageOption(ENGLISH_TAG, "English"),
            LanguageOption(HINDI_TAG, "हिन्दी")
        )
        private const val PREFERENCES = "app_language_preferences"
        private const val KEY_LANGUAGE = "language_tag"
    }
}
