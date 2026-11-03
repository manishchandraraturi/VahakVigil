package com.manish.vahakvigil.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.manish.vahakvigil.data.local.db.entity.MedicalProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the medical profile table.
 */
@Dao
interface MedicalProfileDao {

    @Query("SELECT * FROM medical_profile WHERE id = :profileId LIMIT 1")
    fun getProfile(profileId: Int = MedicalProfileEntity.SINGLE_ROW_ID): Flow<MedicalProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: MedicalProfileEntity)
}
