package com.manish.vahakvigil.repository

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeContactRepository(
    initialContacts: List<Contact> = emptyList()
) : ContactRepository {

    private val flow = MutableStateFlow(
        initialContacts.sortedWith(compareBy({ it.priority }, { it.id }))
    )
    private var nextId = (initialContacts.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun getContacts(): Flow<List<Contact>> = flow.asStateFlow()

    override suspend fun getContactById(id: Long): Contact? {
        return flow.value.find { it.id == id }
    }

    override suspend fun getContactCount(): Int {
        return flow.value.size
    }

    override suspend fun saveContact(contact: Contact): Long {
        val assignedId = if (contact.id == 0L) nextId++ else contact.id
        val assignedPriority = if (contact.id == 0L && flow.value.isNotEmpty() && contact.priority == 1) {
            (flow.value.maxOfOrNull { it.priority } ?: 0) + 1
        } else {
            contact.priority
        }
        val newContact = contact.copy(id = assignedId, priority = assignedPriority)
        flow.update { list ->
            (list.filterNot { it.id == assignedId } + newContact)
                .sortedWith(compareBy({ it.priority }, { it.id }))
        }
        return assignedId
    }

    override suspend fun updatePriorities(contacts: List<Contact>) {
        flow.update { list ->
            val updateMap = contacts.associateBy { it.id }
            list.map { existing ->
                updateMap[existing.id] ?: existing
            }.sortedWith(compareBy({ it.priority }, { it.id }))
        }
    }

    override suspend fun deleteContact(contact: Contact) {
        flow.update { list ->
            val remaining = list.filterNot { it.id == contact.id }
            remaining.mapIndexed { index, c ->
                c.copy(priority = index + 1)
            }.sortedWith(compareBy({ it.priority }, { it.id }))
        }
    }
}
