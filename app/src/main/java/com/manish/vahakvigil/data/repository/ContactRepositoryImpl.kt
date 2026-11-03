package com.manish.vahakvigil.data.repository

import com.manish.vahakvigil.data.local.db.dao.ContactDao
import com.manish.vahakvigil.data.local.db.entity.ContactEntity
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.repository.ContactRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [ContactRepository].
 * Handles mapping between the domain [Contact] and [ContactEntity].
 */
class ContactRepositoryImpl(
    private val dao: ContactDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContactRepository {

    override fun getContacts(): Flow<List<Contact>> {
        return dao.getAllContacts().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(ioDispatcher)
    }

    override suspend fun getContactById(id: Long): Contact? {
        return withContext(ioDispatcher) {
            dao.getContactById(id)?.toDomain()
        }
    }

    override suspend fun getContactCount(): Int {
        return withContext(ioDispatcher) {
            dao.getContactCount()
        }
    }

    override suspend fun saveContact(contact: Contact): Long {
        return withContext(ioDispatcher) {
            dao.insertContact(contact.toEntity())
        }
    }

    override suspend fun updatePriorities(contacts: List<Contact>) {
        withContext(ioDispatcher) {
            dao.updateContacts(contacts.map { it.toEntity() })
        }
    }

    override suspend fun deleteContact(contact: Contact) {
        withContext(ioDispatcher) {
            dao.deleteContact(contact.toEntity())
            val remaining = dao.getAllContactsSync()
            val normalized = remaining.mapIndexed { index, entity ->
                entity.copy(priority = index + 1)
            }
            dao.updateContacts(normalized)
        }
    }
}

// Mapper extensions
private fun ContactEntity.toDomain(): Contact =
    Contact(
        id = id,
        name = name,
        relationship = relationship,
        phone = phone,
        priority = priority,
        isPhoneVerified = isPhoneVerified
    )

private fun Contact.toEntity(): ContactEntity =
    ContactEntity(
        id = id,
        name = name,
        relationship = relationship,
        phone = phone,
        priority = priority,
        isPhoneVerified = isPhoneVerified
    )
