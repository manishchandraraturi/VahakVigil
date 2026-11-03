package com.manish.vahakvigil.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideSessionMigrationTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrationThreeToFourPreservesExistingContactsAndAddsCascadeRideTables() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO emergency_contacts (id, name, relationship, phone, priority, isPhoneVerified) VALUES (42, 'Existing contact', 'Friend', '+10000000000', 1, 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)
        migrated.query("SELECT name FROM emergency_contacts WHERE id = 42").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Existing contact", cursor.getString(0))
        }
        migrated.execSQL("INSERT INTO ride_sessions (id, startedAtEpochMillis, durationMillis, status) VALUES ('session-1', 1000, 0, 'ACTIVE')")
        migrated.execSQL("INSERT INTO ride_session_points (sessionId, latitude, longitude, timestampEpochMillis) VALUES ('session-1', 12.0, 77.0, 1001)")
        migrated.execSQL("DELETE FROM ride_sessions WHERE id = 'session-1'")
        migrated.query("SELECT COUNT(*) FROM ride_session_points WHERE sessionId = 'session-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationFourToFiveCreatesLocalEmergencyIncidentHistory() {
        helper.createDatabase(TEST_DB_V4, 4).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB_V4, 5, true, AppDatabase.MIGRATION_4_5)
        migrated.execSQL("INSERT INTO emergency_incidents (incidentId, incidentTimestampEpochMillis, windowStartEpochMillis, windowEndEpochMillis, confidenceScore, evidenceSummary, status, userAction) VALUES ('incident-1', 1000, 900, 1000, 0.8, 'sensor evidence', 'COUNTING_DOWN', 'NO_ACTION_RECORDED')")
        migrated.query("SELECT confidenceScore FROM emergency_incidents WHERE incidentId = 'incident-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0.8, cursor.getDouble(0), 0.001)
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "ride-session-migration-test"
        const val TEST_DB_V4 = "incident-history-migration-test"
    }
}
