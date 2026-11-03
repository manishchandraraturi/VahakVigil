package com.manish.vahakvigil.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

/**
 * Supported predefined time regions for user selection.
 */
data class TimeRegion(
    val id: String?,
    val label: String,
    val code: String,
    val zoneId: String? // null represents system default
) {
    val displayName: String get() = label
}

/**
 * Local DataStore manager for persisting and retrieving user timezone preferences.
 */
class TimezoneManager(private val context: Context) {

    val selectedZoneId: Flow<String?> = context.userPreferencesDataStore.data.map { preferences ->
        preferences[TimezoneKey]?.takeIf { it.isNotBlank() }
    }

    suspend fun setZoneId(zoneId: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            if (zoneId.isNullOrBlank()) {
                preferences.remove(TimezoneKey)
            } else {
                preferences[TimezoneKey] = zoneId
            }
        }
    }

    companion object {
        private val TimezoneKey = stringPreferencesKey("selected_timezone_zone_id")

        val supportedRegions: List<TimeRegion> = listOf(
            TimeRegion(null, "System Default (Auto)", "Auto", null),
            TimeRegion("Asia/Kolkata", "India", "IST", "Asia/Kolkata"),
            TimeRegion("America/New_York", "United States (Eastern)", "EST/EDT", "America/New_York"),
            TimeRegion("America/Los_Angeles", "United States (Pacific)", "PST/PDT", "America/Los_Angeles"),
            TimeRegion("Europe/London", "United Kingdom", "GMT/BST", "Europe/London"),
            TimeRegion("Asia/Dubai", "United Arab Emirates", "GST", "Asia/Dubai"),
            TimeRegion("Asia/Tokyo", "Japan", "JST", "Asia/Tokyo"),
            TimeRegion("Asia/Singapore", "Singapore", "SGT", "Asia/Singapore"),
            TimeRegion("Europe/Berlin", "Central Europe / Germany", "CET/CEST", "Europe/Berlin"),
            TimeRegion("Australia/Sydney", "Australia (Sydney)", "AEST/AEDT", "Australia/Sydney")
        )

        fun formatCurrentTime(zoneIdString: String?, instant: Instant = Instant.now()): String {
            return formatTime(instant, resolveZoneId(zoneIdString))
        }

        /**
         * Resolves the active [ZoneId], falling back to [ZoneId.systemDefault] if null or invalid.
         */
        fun resolveZoneId(zoneIdString: String?): ZoneId {
            return if (zoneIdString.isNullOrBlank()) {
                try {
                    ZoneId.systemDefault()
                } catch (e: Exception) {
                    ZoneId.of("UTC")
                }
            } else {
                try {
                    ZoneId.of(zoneIdString)
                } catch (e: Exception) {
                    ZoneId.systemDefault()
                }
            }
        }

        /**
         * Formats the current date dynamically (e.g. "Sunday, 20 September").
         */
        fun formatDate(instant: Instant = Instant.now(), zoneId: ZoneId): String {
            val zonedDateTime = instant.atZone(zoneId)
            val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)
            return zonedDateTime.format(formatter)
        }

        /**
         * Formats the current time dynamically (e.g. "7:45 PM").
         */
        fun formatTime(instant: Instant = Instant.now(), zoneId: ZoneId): String {
            val zonedDateTime = instant.atZone(zoneId)
            val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            return zonedDateTime.format(formatter)
        }

        /**
         * Formats the region name and timezone offset (e.g. "India", "IST · UTC+05:30").
         */
        fun formatRegionDetails(instant: Instant = Instant.now(), zoneId: ZoneId): Pair<String, String> {
            val zonedDateTime = instant.atZone(zoneId)
            val regionName = supportedRegions.find { it.zoneId == zoneId.id }?.displayName
                ?: zoneId.id.substringAfterLast('/').replace('_', ' ')

            val zoneName = try {
                zoneId.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
            } catch (e: Exception) {
                ""
            }

            val offset = zonedDateTime.offset.id.let {
                if (it == "Z") "UTC+00:00" else "UTC$it"
            }

            val offsetString = if (zoneName.isNotBlank() && !zoneName.startsWith("GMT") && !zoneName.startsWith("UTC")) {
                "$zoneName · $offset"
            } else {
                offset
            }

            return Pair(regionName, offsetString)
        }

        /**
         * Determines greeting based on hour of day in the given timezone.
         */
        fun formatGreeting(instant: Instant = Instant.now(), zoneId: ZoneId): String {
            val hour = instant.atZone(zoneId).hour
            return when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Good evening"
            }
        }
    }
}
