package com.manish.vahakvigil.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing the user's medical profile table.
 *
 * Designed as a single-user record using a constant primary key ([SINGLE_ROW_ID]).
 */
@Entity(tableName = "medical_profile")
data class MedicalProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val name: String,
    val age: String,
    val bloodGroup: String,
    val conditions: String,
    val allergies: String,
    val medications: String,
    val height: String,
    val weight: String,
    val notes: String
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
