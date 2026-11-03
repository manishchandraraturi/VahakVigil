package com.manish.vahakvigil.ui.screens.contacts

/**
 * UI State for the Add/Edit Contact screen.
 *
 * @param id The contact ID. 0L represents a new contact being created;
 *           non-zero represents an existing contact being edited.
 * @param name The contact's name.
 * @param relationship The relationship label (e.g. "Mother", "Spouse", "Friend").
 * @param phone The contact's phone number.
 * @param priority Position in emergency contact sequence (1, 2, or 3).
 * @param nameError Validation error message for name, or null if valid.
 * @param relationshipError Validation error message for relationship, or null if valid.
 * @param phoneError Validation error message for phone, or null if valid.
 * @param generalError General error message, or null if valid.
 * @param isLoading True while loading an existing contact's details from Room.
 * @param isSaving True while the save operation is in progress.
 * @param isSaveCompleted True when saving succeeded and the screen should navigate back.
 * @param isDeleted True when the contact was deleted and the screen should navigate back.
 */
data class AddEditContactUiState(
    val id: Long = 0L,
    val name: String = "",
    val relationship: String = "",
    val phone: String = "",
    val priority: Int = 1,
    val nameError: String? = null,
    val relationshipError: String? = null,
    val phoneError: String? = null,
    val generalError: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaveCompleted: Boolean = false,
    val isDeleted: Boolean = false
) {
    val isEditing: Boolean
        get() = id != 0L

    val canSave: Boolean
        get() = !isSaving && !isLoading
}
