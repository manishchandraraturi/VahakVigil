package com.manish.vahakvigil.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.repository.ContactRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Emergency Contacts screen.
 *
 * Responsibilities:
 * - Emits reactive [EmergencyContactsUiState] collected directly from the Room [ContactRepository].
 * - The contacts table starts empty; no fake seed data is injected.
 * - Exposes [onAddContactClick] as the entry point for adding contacts.
 */
class EmergencyContactsViewModel(
    private val repository: ContactRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val uiState: StateFlow<EmergencyContactsUiState> = repository.getContacts()
        .map { contacts ->
            EmergencyContactsUiState(
                contacts = contacts,
                canAddContact = contacts.size < 3
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EmergencyContactsUiState()
        )

    /**
     * Moves a contact up in priority order (swapping with the previous contact).
     */
    fun moveContactUp(contact: Contact) {
        val list = uiState.value.contacts
        val index = list.indexOfFirst { it.id == contact.id }
        if (index > 0) {
            val mutableList = list.toMutableList()
            val temp = mutableList[index]
            mutableList[index] = mutableList[index - 1]
            mutableList[index - 1] = temp
            val updated = mutableList.mapIndexed { idx, c ->
                c.copy(priority = idx + 1)
            }
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    repository.updatePriorities(updated)
                }
            }
        }
    }

    /**
     * Moves a contact down in priority order (swapping with the next contact).
     */
    fun moveContactDown(contact: Contact) {
        val list = uiState.value.contacts
        val index = list.indexOfFirst { it.id == contact.id }
        if (index >= 0 && index < list.lastIndex) {
            val mutableList = list.toMutableList()
            val temp = mutableList[index]
            mutableList[index] = mutableList[index + 1]
            mutableList[index + 1] = temp
            val updated = mutableList.mapIndexed { idx, c ->
                c.copy(priority = idx + 1)
            }
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    repository.updatePriorities(updated)
                }
            }
        }
    }

    /**
     * Deletes a contact via [ContactRepository] on [ioDispatcher].
     */
    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                repository.deleteContact(contact)
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: ContactRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EmergencyContactsViewModel(repository) as T
            }
        }
    }
}
