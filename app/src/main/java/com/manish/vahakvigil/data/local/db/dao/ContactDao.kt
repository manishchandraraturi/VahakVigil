package com.manish.vahakvigil.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.manish.vahakvigil.data.local.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the emergency contacts table.
 */
@Dao
interface ContactDao {

    @Query("SELECT * FROM emergency_contacts ORDER BY priority ASC, id ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM emergency_contacts ORDER BY priority ASC, id ASC")
    suspend fun getAllContactsSync(): List<ContactEntity>

    @Query("SELECT * FROM emergency_contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Long): ContactEntity?

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getContactCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Update
    suspend fun updateContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)
}
