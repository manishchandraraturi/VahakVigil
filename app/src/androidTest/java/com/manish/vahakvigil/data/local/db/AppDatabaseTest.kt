package com.manish.vahakvigil.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manish.vahakvigil.data.local.db.dao.ContactDao
import com.manish.vahakvigil.data.local.db.dao.MedicalProfileDao
import com.manish.vahakvigil.data.local.db.entity.ContactEntity
import com.manish.vahakvigil.data.local.db.entity.MedicalProfileEntity
import com.manish.vahakvigil.data.repository.RideSessionRepositoryImpl
import com.manish.vahakvigil.data.repository.SensorEventRepositoryImpl
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.data.repository.EmergencyIncidentRepositoryImpl
import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
import com.manish.vahakvigil.data.repository.EmergencyLifecycleRepositoryImpl
import com.manish.vahakvigil.domain.model.EmergencyLifecycleRecord
import com.manish.vahakvigil.domain.model.EmergencyLifecyclePhase
import com.manish.vahakvigil.domain.model.EmergencyDispatchAttemptState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var medicalProfileDao: MedicalProfileDao
    private lateinit var contactDao: ContactDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        medicalProfileDao = db.medicalProfileDao()
        contactDao = db.contactDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadMedicalProfile() = runTest {
        val entity = MedicalProfileEntity(
            id = MedicalProfileEntity.SINGLE_ROW_ID,
            name = "Manish Sharma",
            age = "29",
            bloodGroup = "O+",
            conditions = "None",
            allergies = "None",
            medications = "None",
            height = "178 cm",
            weight = "74 kg",
            notes = "Emergency contact primary"
        )
        medicalProfileDao.saveProfile(entity)

        val retrieved = medicalProfileDao.getProfile().first()
        assertNotNull(retrieved)
        assertEquals("Manish Sharma", retrieved?.name)
        assertEquals("O+", retrieved?.bloodGroup)
    }

    @Test
    fun saveProfileOverwritesExistingSingleRow() = runTest {
        val first = MedicalProfileEntity(
            name = "Initial",
            age = "20",
            bloodGroup = "A+",
            conditions = "",
            allergies = "",
            medications = "",
            height = "",
            weight = "",
            notes = ""
        )
        medicalProfileDao.saveProfile(first)

        val updated = MedicalProfileEntity(
            name = "Updated",
            age = "25",
            bloodGroup = "AB+",
            conditions = "",
            allergies = "",
            medications = "",
            height = "",
            weight = "",
            notes = ""
        )
        medicalProfileDao.saveProfile(updated)

        val retrieved = medicalProfileDao.getProfile().first()
        assertEquals("Updated", retrieved?.name)
        assertEquals("25", retrieved?.age)
        assertEquals("AB+", retrieved?.bloodGroup)
    }

    @Test
    fun emergencyLifecyclePersistsAndAtomicallyPreventsDuplicateOrUnknownRetry() = runTest {
        val repository = EmergencyLifecycleRepositoryImpl(db.emergencyLifecycleDao())
        val record = EmergencyLifecycleRecord(
            incidentId = "stable-incident-id",
            incidentCreatedAtEpochMillis = 1_700_000_000_000,
            windowStartEpochMillis = 1_699_999_990_000,
            windowEndEpochMillis = 1_700_000_000_000,
            confidenceScore = .8f,
            phase = EmergencyLifecyclePhase.COUNTING_DOWN,
            deadlineElapsedRealtimeMillis = 75_000,
            deadlineEpochMillis = 1_700_000_075_000,
            bootCount = 4
        )
        repository.createIfMissing(record)
        assertEquals(record, repository.get(record.incidentId))
        assertTrue(repository.beginDispatchAttempt(record.incidentId, false, 1_700_000_075_000))
        assertFalse(repository.beginDispatchAttempt(record.incidentId, false, 1_700_000_075_001))
        repository.updateOutcome(record.incidentId, EmergencyLifecyclePhase.DISPATCH_UNKNOWN, EmergencyDispatchAttemptState.UNKNOWN, 1_700_000_075_002)
        assertFalse(repository.beginDispatchAttempt(record.incidentId, true, 1_700_000_076_000))
        assertEquals(EmergencyDispatchAttemptState.UNKNOWN, repository.get(record.incidentId)?.dispatchState)
    }

    @Test
    fun contactsTableStartsEmpty() = runTest {
        val contacts = contactDao.getAllContacts().first()
        assertTrue(contacts.isEmpty())
    }

    @Test
    fun insertAndRetrieveContacts() = runTest {
        val contact1 = ContactEntity(name = "Ravi Sharma", relationship = "Brother", phone = "+919876543210")
        val contact2 = ContactEntity(name = "Asha Sharma", relationship = "Mother", phone = "+919876543001")

        contactDao.insertContact(contact1)
        contactDao.insertContact(contact2)

        val contacts = contactDao.getAllContacts().first()
        assertEquals(2, contacts.size)
        assertEquals("Ravi Sharma", contacts[0].name)
        assertEquals("Asha Sharma", contacts[1].name)
    }

    @Test
    fun getContactByIdReturnsMatchingRow() = runTest {
        val contact1 = ContactEntity(name = "Ravi", relationship = "Brother", phone = "111")
        val id = contactDao.insertContact(contact1)

        val retrieved = contactDao.getContactById(id)
        assertNotNull(retrieved)
        assertEquals("Ravi", retrieved?.name)
        assertEquals(id, retrieved?.id)
    }

    @Test
    fun deleteContactRemovesRow() = runTest {
        val contact = ContactEntity(id = 1L, name = "Temp Contact", relationship = "Friend", phone = "123")
        contactDao.insertContact(contact)

        val beforeDelete = contactDao.getAllContacts().first()
        assertEquals(1, beforeDelete.size)

        contactDao.deleteContact(beforeDelete.first())
        val afterDelete = contactDao.getAllContacts().first()
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun rideSessionIsUniqueFinalizedOnceAndHistoryIsNewestFirst() = runTest {
        val repository = RideSessionRepositoryImpl(db, db.rideSessionDao())
        val first = repository.startOrResume(1_000L)
        assertEquals(first.id, repository.startOrResume(2_000L).id)
        repository.appendPoint(first.id, RideSessionPoint(12.0, 77.0, 1_100L, 5f, 2f))
        repository.appendPoint(first.id, RideSessionPoint(12.0001, 77.0, 2_100L, 5f, 4f))
        assertTrue(repository.finish(first.id, 10_000L))
        assertFalse(repository.finish(first.id, 11_000L))
        assertEquals(RideSessionStatus.COMPLETED, repository.getSession(first.id)?.status)
        assertTrue((repository.getSession(first.id)?.distanceMeters ?: 0.0) > 0.0)

        val second = repository.startOrResume(20_000L)
        assertTrue(repository.finish(second.id, 25_000L))
        assertEquals(RideSessionStatus.INCOMPLETE, repository.getSession(second.id)?.status)
        val history = repository.observeSessions().first()
        assertEquals(second.id, history.first().id)
        assertEquals(first.id, history.last().id)
    }

    @Test
    fun deletingRideCascadesItsRecordedPath() = runTest {
        val repository = RideSessionRepositoryImpl(db, db.rideSessionDao())
        val ride = repository.startOrResume(1_000L)
        repository.appendPoint(ride.id, RideSessionPoint(12.0, 77.0, 1_100L, 4f, null))
        assertEquals(1, repository.getPoints(ride.id).size)
        repository.deleteSession(ride.id)
        assertTrue(repository.getPoints(ride.id).isEmpty())
        assertNull(repository.getSession(ride.id))
    }

    @Test
    fun sensorEventBatchIsOrderedPagedAndIsolatedByRideSession() = runTest {
        val rideRepository = RideSessionRepositoryImpl(db, db.rideSessionDao())
        val eventRepository = SensorEventRepositoryImpl(db.sensorEventDao())
        val firstRide = rideRepository.startOrResume(1_000L)
        rideRepository.finish(firstRide.id, 1_001L, interrupted = true)
        val secondRide = rideRepository.startOrResume(2_000L)
        val earlier = rawEvent(RawSensorType.ACCELEROMETER, 10L)
        val later = rawEvent(RawSensorType.GYROSCOPE, 20L)
        val tiedLater = rawEvent(RawSensorType.ACCELEROMETER, 20L)

        eventRepository.insertBatch(firstRide.id, listOf(later, earlier, tiedLater))
        eventRepository.insertBatch(secondRide.id, listOf(rawEvent(RawSensorType.GYROSCOPE, 30L)))

        assertEquals(3L, eventRepository.countForSession(firstRide.id))
        assertEquals(1L, eventRepository.countForSession(secondRide.id))
        assertEquals(
            listOf(10L, 20L),
            eventRepository.getEventsForSession(firstRide.id, limit = 2, offset = 0).map { it.timestampNanos }
        )
        assertEquals(
            listOf(20L),
            eventRepository.getEventsForSession(firstRide.id, limit = 2, offset = 2).map { it.timestampNanos }
        )
        assertEquals(
            listOf(30L),
            eventRepository.getEventsForSession(secondRide.id, limit = 10, offset = 0).map { it.timestampNanos }
        )
    }

    @Test
    fun deletingRideCascadesItsSensorEvents() = runTest {
        val rideRepository = RideSessionRepositoryImpl(db, db.rideSessionDao())
        val eventRepository = SensorEventRepositoryImpl(db.sensorEventDao())
        val ride = rideRepository.startOrResume(1_000L)
        eventRepository.insertBatch(ride.id, listOf(rawEvent(RawSensorType.ACCELEROMETER, 10L)))

        assertEquals(1L, eventRepository.countForSession(ride.id))
        rideRepository.deleteSession(ride.id)
        assertEquals(0L, eventRepository.countForSession(ride.id))
    }

    @Test
    fun sensorEventExportKeysetIsStableForEqualTimestampsAndSnapshotBoundary() = runTest {
        val ride = RideSessionRepositoryImpl(db, db.rideSessionDao()).startOrResume(1_000L)
        val events = SensorEventRepositoryImpl(db.sensorEventDao())
        events.insertBatch(
            ride.id,
            listOf(
                rawEvent(RawSensorType.ACCELEROMETER, 10_000_000L),
                rawEvent(RawSensorType.GYROSCOPE, 10_000_000L),
                rawEvent(RawSensorType.ACCELEROMETER, 10_000_000L)
            )
        )
        val snapshot = events.getExportSnapshot(ride.id)
        val first = events.getExportPage(ride.id, snapshot.maxEventId, after = null, limit = 2)
        events.insertBatch(ride.id, listOf(rawEvent(RawSensorType.GYROSCOPE, 1_000_000L)))
        val last = first.last()
        val second = events.getExportPage(
            ride.id,
            snapshot.maxEventId,
            SensorEventExportCursor(last.event.timestampNanos, last.eventId),
            limit = 2
        )

        assertEquals(3L, snapshot.eventCount)
        assertEquals(2, first.size)
        assertEquals(1, second.size)
        assertTrue(first.zipWithNext().all { (a, b) -> a.eventId < b.eventId })
        assertTrue(second.single().eventId > first.last().eventId)
        assertTrue(second.none { it.event.timestampNanos == 1_000_000L })
    }

    private fun rawEvent(type: RawSensorType, timestampNanos: Long) = RawSensorEvent(
        type = type,
        timestampNanos = timestampNanos,
        timestampEpochMillis = timestampNanos / 1_000_000L,
        x = timestampNanos.toFloat(),
        y = 2f,
        z = 3f,
        accuracy = 3
    )

    @Test
    fun emergencyIncidentKeepsEvidenceAttachedAndDeletesByIncidentId() = runTest {
        val repository = EmergencyIncidentRepositoryImpl(db.emergencyIncidentDao())
        val first = EmergencyIncidentRecord(
            incidentId = "1700000000000",
            incidentTimestampEpochMillis = 1_700_000_000_000L,
            windowStartEpochMillis = 1_699_999_900_000L,
            windowEndEpochMillis = 1_700_000_000_000L,
            latitude = 12.0,
            longitude = 77.0,
            confidenceScore = 0.85f,
            evidenceSummary = "qualified sensor windows",
            status = "COUNTING_DOWN",
            userAction = "NO_ACTION_RECORDED"
        )
        repository.recordIncident(first)
        repository.updateEvidence(first.incidentId, "rider is conscious", "/private/incident-photo.jpg")
        repository.recordIncident(first.copy(status = "CANCELLED"))
        val persisted = repository.getIncident(first.incidentId)
        assertEquals("rider is conscious", persisted?.voiceNote)
        assertEquals("/private/incident-photo.jpg", persisted?.photoReference)
        assertEquals("CANCELLED", persisted?.status)
        repository.deleteIncident(first.incidentId)
        assertNull(repository.getIncident(first.incidentId))
    }
}
