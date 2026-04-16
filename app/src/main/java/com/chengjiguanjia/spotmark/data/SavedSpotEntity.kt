package com.chengjiguanjia.spotmark.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_spots")
data class SavedSpotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val createdAt: Long,
    val updatedAt: Long,
    val photoPaths: List<String>,
)
