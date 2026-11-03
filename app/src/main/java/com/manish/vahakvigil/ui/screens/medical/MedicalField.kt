package com.manish.vahakvigil.ui.screens.medical

/**
 * Enumerates every editable field in the Medical Profile form.
 *
 * Using an enum lets [MedicalProfileViewModel] expose a single
 * [MedicalProfileViewModel.updateField] function instead of nine separate
 * updaters, and makes adding or renaming fields a one-line change.
 */
enum class MedicalField {
    NAME,
    AGE,
    BLOOD_GROUP,
    CONDITIONS,
    ALLERGIES,
    MEDICATIONS,
    HEIGHT,
    WEIGHT,
    NOTES
}
