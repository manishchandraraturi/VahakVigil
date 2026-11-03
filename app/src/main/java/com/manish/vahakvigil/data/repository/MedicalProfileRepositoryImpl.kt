package com.manish.vahakvigil.data.repository

import com.manish.vahakvigil.data.local.db.dao.MedicalProfileDao
import com.manish.vahakvigil.data.local.db.entity.MedicalProfileEntity
import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [MedicalProfileRepository].
 * Handles mapping between the domain [MedicalProfile] and [MedicalProfileEntity].
 */
class MedicalProfileRepositoryImpl(
    private val dao: MedicalProfileDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MedicalProfileRepository {

    override fun getProfile(): Flow<MedicalProfile?> {
        return dao.getProfile().map { entity ->
            entity?.toDomain()
        }.flowOn(ioDispatcher)
    }

    override suspend fun saveProfile(profile: MedicalProfile) {
        withContext(ioDispatcher) {
            dao.saveProfile(profile.toEntity())
        }
    }
}

// Mapper extensions
private fun MedicalProfileEntity.toDomain(): MedicalProfile =
    MedicalProfile(
        name = name,
        age = age,
        bloodGroup = bloodGroup,
        conditions = conditions,
        allergies = allergies,
        medications = medications,
        height = height,
        weight = weight,
        notes = notes
    )

private fun MedicalProfile.toEntity(): MedicalProfileEntity =
    MedicalProfileEntity(
        id = MedicalProfileEntity.SINGLE_ROW_ID,
        name = name,
        age = age,
        bloodGroup = bloodGroup,
        conditions = conditions,
        allergies = allergies,
        medications = medications,
        height = height,
        weight = weight,
        notes = notes
    )
