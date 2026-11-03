package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining emergency contact operations.
 * Decoupled from Room or any persistence mechanism.
 */
interface ContactRepository {
    fun getContacts(): Flow<List<Contact>>
    suspend fun getContactById(id: Long): Contact?
    suspend fun getContactCount(): Int
    suspend fun saveContact(contact: Contact): Long
    suspend fun updatePriorities(contacts: List<Contact>)
    suspend fun deleteContact(contact: Contact)
}
