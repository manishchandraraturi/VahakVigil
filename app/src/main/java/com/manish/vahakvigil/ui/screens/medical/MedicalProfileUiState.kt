package com.manish.vahakvigil.ui.screens.medical

/**
 * Represents the complete renderable state of the Medical Profile screen
 * at any given moment.
 *
 * This is a flat data class because the screen is always in a single editing
 * state — there is no loading/error/empty branching needed until the Room
 * persistence layer is introduced (Stage 3). Keeping it flat avoids
 * premature sealed-class complexity.
 *
 * All fields default to empty strings. Pre-population from the database
 * will be handled when the Room data layer is added.
 *
 * @param isSaving  True while the save coroutine is in flight; used to
 *                  update the button label and guard against double-taps.
 * @param saveSuccess  True for ~2 seconds after a successful save;
 *                     used to display transient "Saved ✓" feedback on the
 *                     button before it resets.
 */
data class MedicalProfileUiState(
    val name: String = "",
    val age: String = "",
    val bloodGroup: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val medications: String = "",
    val height: String = "",
    val weight: String = "",
    val notes: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)
