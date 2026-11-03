package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.MedicalProfile
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining medical profile persistence operations.
 * Decoupled from any specific database or storage engine.
 */
interface MedicalProfileRepository {
    fun getProfile(): Flow<MedicalProfile?>
    suspend fun saveProfile(profile: MedicalProfile)
}
