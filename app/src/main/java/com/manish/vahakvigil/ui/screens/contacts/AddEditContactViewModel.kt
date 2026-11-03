package com.manish.vahakvigil.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.repository.ContactRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for adding and editing an emergency contact.
 *
 * Scoped to the Add/Edit contact destination and handles input validation,
 * fetching existing contact data by ID, saving directly, and deletion.
 */
class AddEditContactViewModel(
    private val repository: ContactRepository,
    private val contactId: Long = 0L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditContactUiState(
            id = contactId,
            isLoading = contactId != 0L
        )
    )
    val uiState: StateFlow<AddEditContactUiState> = _uiState.asStateFlow()

    init {
        if (contactId != 0L) {
            loadContact(contactId)
        }
    }

    private fun loadContact(id: Long) {
        viewModelScope.launch {
            try {
                val contact = withContext(ioDispatcher) {
                    repository.getContactById(id)
                }
                if (contact != null) {
                    _uiState.update { current ->
                        current.copy(
                            id = contact.id,
                            name = contact.name,
                            relationship = contact.relationship,
                            phone = contact.phone,
                            priority = contact.priority,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                nameError = if (it.nameError != null && name.isNotBlank()) null else it.nameError
            )
        }
    }

    fun onRelationshipChanged(relationship: String) {
        _uiState.update {
            it.copy(
                relationship = relationship,
                relationshipError = if (it.relationshipError != null && relationship.isNotBlank()) null else it.relationshipError
            )
        }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update {
            it.copy(
                phone = phone,
                phoneError = if (it.phoneError != null && phone.isNotBlank()) null else it.phoneError
            )
        }
    }

    /**
     * Validates contact fields:
     * - Name cannot be blank
     * - Relationship cannot be blank
     * - Phone cannot be blank and must contain a reasonable number of digits (7..15)
     */
    fun validate(): Boolean {
        val current = _uiState.value

        val nameError = if (current.name.isBlank()) {
            "Name cannot be blank"
        } else null

        val relationshipError = if (current.relationship.isBlank()) {
            "Relationship cannot be blank"
        } else null

        val digitsCount = current.phone.count { it.isDigit() }
        val phoneError = when {
            current.phone.isBlank() -> "Phone number cannot be blank"
            digitsCount < 7 || digitsCount > 15 -> "Phone number must contain 7 to 15 digits"
            else -> null
        }

        _uiState.update {
            it.copy(
                nameError = nameError,
                relationshipError = relationshipError,
                phoneError = phoneError
            )
        }

        return nameError == null && relationshipError == null && phoneError == null
    }

    /**
     * Validates and persists the contact directly to Room via [ContactRepository].
     */
    fun saveContact() {
        if (!validate() || _uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalError = null) }
            val current = _uiState.value

            // Enforce duplicate phone number check: reject if another contact has the same normalized digits
            val existingContacts = withContext(ioDispatcher) {
                repository.getContacts().first()
            }
            if (isDuplicatePhone(current.phone, existingContacts, current.id)) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        phoneError = "A contact with this phone number already exists."
                    )
                }
                return@launch
            }

            // Enforce maximum 3 contacts limit when adding a new contact
            if (current.id == 0L) {
                val currentCount = withContext(ioDispatcher) {
                    repository.getContactCount()
                }
                if (currentCount >= 3) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            generalError = "Maximum 3 emergency contacts reached."
                        )
                    }
                    return@launch
                }
            }

            val priorityToSave = if (current.id == 0L) {
                val count = withContext(ioDispatcher) { repository.getContactCount() }
                count + 1
            } else {
                current.priority
            }

            val contact = Contact(
                id = current.id,
                name = current.name.trim(),
                relationship = current.relationship.trim(),
                phone = current.phone.trim(),
                priority = priorityToSave
            )
            withContext(ioDispatcher) {
                repository.saveContact(contact)
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isSaveCompleted = true
                )
            }
        }
    }

    /**
     * Deletes the currently edited contact from Room via [ContactRepository].
     */
    fun deleteContact() {
        val current = _uiState.value
        if (current.id == 0L || current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val contact = Contact(
                id = current.id,
                name = current.name,
                relationship = current.relationship,
                phone = current.phone,
                priority = current.priority
            )
            withContext(ioDispatcher) {
                repository.deleteContact(contact)
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isDeleted = true
                )
            }
        }
    }

    companion object {
        fun normalizePhoneNumber(phone: String): String = phone.filter { it.isDigit() }

        fun isDuplicatePhone(
            phone: String,
            existingContacts: List<Contact>,
            currentContactId: Long = 0L
        ): Boolean {
            val normalizedTarget = normalizePhoneNumber(phone)
            if (normalizedTarget.isEmpty()) return false
            return existingContacts.any { existing ->
                existing.id != currentContactId && normalizePhoneNumber(existing.phone) == normalizedTarget
            }
        }

        fun provideFactory(
            repository: ContactRepository,
            contactId: Long = 0L
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AddEditContactViewModel(repository, contactId) as T
            }
        }
    }
}
