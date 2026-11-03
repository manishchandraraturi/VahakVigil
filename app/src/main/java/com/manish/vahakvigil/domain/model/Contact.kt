package com.manish.vahakvigil.domain.model

/**
 * Domain model representing a single emergency contact.
 *
 * This class lives in the domain layer so it can be referenced by both
 * the UI layer (ViewModels, composables) and the future data layer
 * (Room repository) without creating upward dependencies.
 *
 * @param id        Stable identifier. Defaults to 0 — Room will assign
 *                  real IDs in Stage 3. Using [Long] matches Room's
 *                  default primary key type, so no migration is needed
 *                  when the Entity is introduced.
 * @param name         Full display name.
 * @param relationship Relationship label shown below the name (e.g. "Brother").
 * @param phone        Phone number string displayed as-is; formatting and
 *                     calling are handled by the emergency layer in a later stage.
 */
data class Contact(
    val id: Long = 0L,
    val name: String,
    val relationship: String,
    val phone: String,
    val priority: Int = 1,
    val isPhoneVerified: Boolean = true
) {
    /**
     * Up to two uppercase initials derived from [name], used for the avatar
     * placeholder. Computed on access; not stored.
     *
     * Examples: "Ravi Sharma" → "RS", "Dr. Meera Iyer" → "DM"
     */
    val initials: String
        get() = name
            .split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .take(2)
            .joinToString("")
}
