package com.manish.vahakvigil.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.manish.vahakvigil.data.local.db.dao.ContactDao
import com.manish.vahakvigil.data.local.db.dao.MedicalProfileDao
import com.manish.vahakvigil.data.local.db.dao.RideSessionDao
import com.manish.vahakvigil.data.local.db.dao.EmergencyIncidentDao
import com.manish.vahakvigil.data.local.db.dao.SensorEventDao
import com.manish.vahakvigil.data.local.db.dao.EmergencyLifecycleDao
import com.manish.vahakvigil.data.local.db.entity.ContactEntity
import com.manish.vahakvigil.data.local.db.entity.MedicalProfileEntity
import com.manish.vahakvigil.data.local.db.entity.RideSessionEntity
import com.manish.vahakvigil.data.local.db.entity.RideSessionPointEntity
import com.manish.vahakvigil.data.local.db.entity.EmergencyIncidentEntity
import com.manish.vahakvigil.data.local.db.entity.SensorEventEntity
import com.manish.vahakvigil.data.local.db.entity.EmergencyLifecycleEntity

/**
 * Main Room database definition for VahakVigil.
 *
 * Schema export is enabled to track schema history in git.
 * Non-destructive migration will be enforced across versions;
 * destructive migrations are explicitly disabled for safety.
 */
@Database(
    entities = [
        MedicalProfileEntity::class,
        ContactEntity::class,
        RideSessionEntity::class,
        RideSessionPointEntity::class,
        EmergencyIncidentEntity::class,
        SensorEventEntity::class,
        EmergencyLifecycleEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicalProfileDao(): MedicalProfileDao
    abstract fun contactDao(): ContactDao
    abstract fun rideSessionDao(): RideSessionDao
    abstract fun emergencyIncidentDao(): EmergencyIncidentDao
    abstract fun emergencyLifecycleDao(): EmergencyLifecycleDao
    abstract fun sensorEventDao(): SensorEventDao

    companion object {
        private const val DATABASE_NAME = "vahak_vigil.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emergency_contacts ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emergency_contacts ADD COLUMN isPhoneVerified INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ride_sessions (id TEXT NOT NULL, startedAtEpochMillis INTEGER NOT NULL, endedAtEpochMillis INTEGER, startLatitude REAL, startLongitude REAL, endLatitude REAL, endLongitude REAL, durationMillis INTEGER NOT NULL, distanceMeters REAL, averageSpeedMetersPerSecond REAL, maximumSpeedMetersPerSecond REAL, status TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS ride_session_points (pointId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, timestampEpochMillis INTEGER NOT NULL, accuracyMeters REAL, speedMetersPerSecond REAL, FOREIGN KEY(sessionId) REFERENCES ride_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ride_session_points_sessionId_timestampEpochMillis ON ride_session_points(sessionId, timestampEpochMillis)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS emergency_incidents (incidentId TEXT NOT NULL, incidentTimestampEpochMillis INTEGER NOT NULL, windowStartEpochMillis INTEGER NOT NULL, windowEndEpochMillis INTEGER NOT NULL, latitude REAL, longitude REAL, confidenceScore REAL NOT NULL, evidenceSummary TEXT NOT NULL, status TEXT NOT NULL, userAction TEXT NOT NULL, voiceNote TEXT, photoReference TEXT, PRIMARY KEY(incidentId))")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sensor_events` (" +
                        "`eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`sensorType` TEXT NOT NULL, " +
                        "`timestampNanos` INTEGER NOT NULL, " +
                        "`timestampEpochMillis` INTEGER NOT NULL, " +
                        "`x` REAL NOT NULL, " +
                        "`y` REAL NOT NULL, " +
                        "`z` REAL NOT NULL, " +
                        "`accuracy` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `ride_sessions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_sensor_events_sessionId_timestampNanos` " +
                        "ON `sensor_events` (`sessionId`, `timestampNanos`)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `emergency_lifecycle` (" +
                        "`incidentId` TEXT NOT NULL, " +
                        "`incidentCreatedAtEpochMillis` INTEGER NOT NULL, " +
                        "`windowStartEpochMillis` INTEGER NOT NULL, " +
                        "`windowEndEpochMillis` INTEGER NOT NULL, " +
                        "`confidenceScore` REAL NOT NULL, " +
                        "`phase` TEXT NOT NULL, " +
                        "`deadlineElapsedRealtimeMillis` INTEGER NOT NULL, " +
                        "`deadlineEpochMillis` INTEGER NOT NULL, " +
                        "`bootCount` INTEGER NOT NULL, " +
                        "`userResponse` TEXT NOT NULL, " +
                        "`dispatchState` TEXT NOT NULL, " +
                        "`dispatchAttemptCount` INTEGER NOT NULL, " +
                        "`dispatchAttemptedAtEpochMillis` INTEGER, " +
                        "`dispatchResolvedAtEpochMillis` INTEGER, " +
                        "`lastLatitude` REAL, " +
                        "`lastLongitude` REAL, " +
                        "`lastLocationTimestampEpochMillis` INTEGER, " +
                        "PRIMARY KEY(`incidentId`))"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
            }
    }
}
