package com.manish.vahakvigil.ui.screens.contacts

import com.manish.vahakvigil.domain.model.Contact

/**
 * Represents the complete renderable state of the Emergency Contacts screen.
 *
 * Kept intentionally flat — the screen has only one piece of dynamic data:
 * the list of contacts. No loading/error/saving states are needed until the
 * Room persistence layer is introduced (Stage 3).
 *
 * @param contacts The list of [Contact]s to display. Starts empty; the
 *                 ViewModel initialises it with temporary hardcoded entries
 *                 until Room provides them from the database.
 */
data class EmergencyContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val canAddContact: Boolean = true
) {
    val isMaxContactsReached: Boolean
        get() = contacts.size >= 3
}
