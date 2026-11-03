package com.manish.vahakvigil.domain.model

/**
 * Pure Kotlin domain representation of the critical medical information
 * needed for an emergency dispatch payload.
 *
 * Derived exclusively from [MedicalProfile]. Does not invent new fields.
 *
 * @param riderName Rider's name from medical profile.
 * @param bloodGroup Blood group (e.g. "O+", "B-").
 * @param allergies Documented allergies.
 * @param conditions Existing medical conditions.
 * @param medications Current medications.
 * @param notes Critical medical notes or special instructions.
 */
data class EmergencyMedicalSummary(
    val riderName: String,
    val bloodGroup: String,
    val allergies: String,
    val conditions: String,
    val medications: String,
    val notes: String
) {
    companion object {
        fun fromMedicalProfile(profile: MedicalProfile): EmergencyMedicalSummary =
            EmergencyMedicalSummary(
                riderName = profile.name,
                bloodGroup = profile.bloodGroup,
                allergies = profile.allergies,
                conditions = profile.conditions,
                medications = profile.medications,
                notes = profile.notes
            )
    }
}
