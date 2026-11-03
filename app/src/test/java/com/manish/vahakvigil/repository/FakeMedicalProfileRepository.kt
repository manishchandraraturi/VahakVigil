package com.manish.vahakvigil.repository

import com.manish.vahakvigil.domain.model.MedicalProfile
import com.manish.vahakvigil.domain.repository.MedicalProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMedicalProfileRepository(
    initialProfile: MedicalProfile? = null
) : MedicalProfileRepository {

    private val flow = MutableStateFlow(initialProfile)

    override fun getProfile(): Flow<MedicalProfile?> = flow.asStateFlow()

    override suspend fun saveProfile(profile: MedicalProfile) {
        flow.value = profile
    }
}
