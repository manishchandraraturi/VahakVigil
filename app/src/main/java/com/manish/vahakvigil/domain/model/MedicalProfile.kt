package com.manish.vahakvigil.domain.model

/**
 * Domain model representing the user's medical and emergency care details.
 *
 * This pure Kotlin model decouples the UI and domain layers from the Room
 * database entity.
 */
data class MedicalProfile(
    val name: String = "",
    val age: String = "",
    val bloodGroup: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val medications: String = "",
    val height: String = "",
    val weight: String = "",
    val notes: String = ""
)
