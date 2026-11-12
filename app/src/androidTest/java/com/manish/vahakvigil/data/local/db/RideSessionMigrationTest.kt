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

    @Test
    fun migrationFiveToSixPreservesRideDataAddsOrderedSensorEventsAndCascadesDelete() {
        helper.createDatabase(TEST_DB_V5, 5).apply {
            execSQL("INSERT INTO ride_sessions (id, startedAtEpochMillis, durationMillis, status) VALUES ('ride-5', 1000, 0, 'ACTIVE')")
            execSQL("INSERT INTO ride_session_points (sessionId, latitude, longitude, timestampEpochMillis) VALUES ('ride-5', 12.0, 77.0, 1001)")
            execSQL("INSERT INTO emergency_incidents (incidentId, incidentTimestampEpochMillis, windowStartEpochMillis, windowEndEpochMillis, confidenceScore, evidenceSummary, status, userAction) VALUES ('incident-5', 1100, 900, 1100, 0.8, 'existing incident', 'COUNTING_DOWN', 'NO_ACTION_RECORDED')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_V5, 6, true, AppDatabase.MIGRATION_5_6)
        migrated.query("SELECT id, startedAtEpochMillis FROM ride_sessions WHERE id = 'ride-5'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("ride-5", cursor.getString(0))
            assertEquals(1000L, cursor.getLong(1))
        }
        migrated.query("SELECT latitude, longitude FROM ride_session_points WHERE sessionId = 'ride-5'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(12.0, cursor.getDouble(0), 0.0)
            assertEquals(77.0, cursor.getDouble(1), 0.0)
        }
        migrated.query("SELECT evidenceSummary FROM emergency_incidents WHERE incidentId = 'incident-5'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("existing incident", cursor.getString(0))
        }

        migrated.execSQL("INSERT INTO sensor_events (sessionId, sensorType, timestampNanos, timestampEpochMillis, x, y, z, accuracy) VALUES ('ride-5', 'GYROSCOPE', 20, 2000, 0.1, 0.2, 0.3, 3)")
        migrated.execSQL("INSERT INTO sensor_events (sessionId, sensorType, timestampNanos, timestampEpochMillis, x, y, z, accuracy) VALUES ('ride-5', 'ACCELEROMETER', 10, 1999, 1.0, 2.0, 3.0, 2)")
        migrated.query("SELECT sensorType, timestampNanos FROM sensor_events WHERE sessionId = 'ride-5' ORDER BY timestampNanos ASC, eventId ASC").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("ACCELEROMETER", cursor.getString(0))
            assertEquals(10L, cursor.getLong(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals("GYROSCOPE", cursor.getString(0))
            assertEquals(20L, cursor.getLong(1))
            assertEquals(false, cursor.moveToNext())
        }
        migrated.query("SELECT COUNT(*) FROM sensor_events WHERE sessionId = 'ride-5'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }

        migrated.execSQL("DELETE FROM ride_sessions WHERE id = 'ride-5'")
        migrated.query("SELECT COUNT(*) FROM sensor_events WHERE sessionId = 'ride-5'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM ride_session_points WHERE sessionId = 'ride-5'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationSixToSevenAddsEmergencyLifecycleWithoutChangingExistingIncidentData() {
        helper.createDatabase(TEST_DB_V6, 6).apply {
            execSQL("INSERT INTO emergency_incidents (incidentId, incidentTimestampEpochMillis, windowStartEpochMillis, windowEndEpochMillis, confidenceScore, evidenceSummary, status, userAction) VALUES ('existing-incident', 2000, 1900, 2000, 0.7, 'keep this history', 'COUNTING_DOWN', 'NO_ACTION_RECORDED')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_V6, 7, true, AppDatabase.MIGRATION_6_7)
        migrated.query("SELECT evidenceSummary FROM emergency_incidents WHERE incidentId = 'existing-incident'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("keep this history", cursor.getString(0))
        }
        migrated.execSQL("INSERT INTO emergency_lifecycle (incidentId, incidentCreatedAtEpochMillis, windowStartEpochMillis, windowEndEpochMillis, confidenceScore, phase, deadlineElapsedRealtimeMillis, deadlineEpochMillis, bootCount, userResponse, dispatchState, dispatchAttemptCount) VALUES ('lifecycle-1', 2000, 1900, 2000, 0.7, 'COUNTING_DOWN', 75000, 77000, 1, 'NONE', 'NOT_STARTED', 0)")
        migrated.query("SELECT phase, dispatchState FROM emergency_lifecycle WHERE incidentId = 'lifecycle-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("COUNTING_DOWN", cursor.getString(0))
            assertEquals("NOT_STARTED", cursor.getString(1))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "ride-session-migration-test"
        const val TEST_DB_V4 = "incident-history-migration-test"
        const val TEST_DB_V5 = "sensor-event-migration-test"
        const val TEST_DB_V6 = "emergency-lifecycle-migration-test"
    }
}
