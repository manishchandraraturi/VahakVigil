package com.manish.vahakvigil.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

class ThemeManager(private val context: Context) {
    val isDarkTheme: Flow<Boolean> = context.themeDataStore.data.map { preferences ->
        preferences[DarkThemeKey] ?: false
    }

    suspend fun setDarkTheme(isDarkTheme: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[DarkThemeKey] = isDarkTheme
        }
    }

    private companion object {
        val DarkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    }
}
