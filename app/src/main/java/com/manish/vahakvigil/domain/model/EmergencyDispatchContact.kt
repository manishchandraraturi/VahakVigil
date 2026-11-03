package com.manish.vahakvigil.domain.model

/**
 * Pure Kotlin domain model representing a contact designated to receive
 * emergency dispatch notifications.
 *
 * Reuses the existing [Contact] domain model's identity, relationship,
 * and telephone fields without duplicating database concerns.
 *
 * @param id Stable identifier matching stored Contact id.
 * @param name Full display name.
 * @param relationship Relationship label (e.g. "Spouse", "Mother").
 * @param phone Telephone number string.
 */
data class EmergencyDispatchContact(
    val id: Long,
    val name: String,
    val relationship: String,
    val phone: String,
    val priority: Int = 1
) {
    companion object {
        fun fromContact(contact: Contact): EmergencyDispatchContact =
            EmergencyDispatchContact(
                id = contact.id,
                name = contact.name,
                relationship = contact.relationship,
                phone = contact.phone,
                priority = contact.priority
            )
    }
}
