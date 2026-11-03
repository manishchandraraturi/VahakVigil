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
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.data.repository.EmergencyIncidentRepositoryImpl
import com.manish.vahakvigil.domain.model.EmergencyIncidentRecord
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
