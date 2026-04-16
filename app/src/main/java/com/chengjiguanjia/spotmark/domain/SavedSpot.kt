package com.chengjiguanjia.spotmark.domain

import com.chengjiguanjia.spotmark.data.SavedSpotEntity

data class SavedSpot(
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

fun SavedSpotEntity.toDomain(): SavedSpot =
    SavedSpot(
        id = id,
        title = title,
        note = note,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        createdAt = createdAt,
        updatedAt = updatedAt,
        photoPaths = photoPaths,
    )

fun SavedSpot.toEntity(): SavedSpotEntity =
    SavedSpotEntity(
        id = id,
        title = title,
        note = note,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        createdAt = createdAt,
        updatedAt = updatedAt,
        photoPaths = photoPaths,
    )
