package com.manish.vahakvigil.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an emergency contact in the database.
 */
@Entity(tableName = "emergency_contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val relationship: String,
    val phone: String,
    @ColumnInfo(defaultValue = "1")
    val priority: Int = 1,
    @ColumnInfo(defaultValue = "1")
    val isPhoneVerified: Boolean = true
)
