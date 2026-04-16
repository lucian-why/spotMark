package com.chengjiguanjia.spotmark.data

import com.chengjiguanjia.spotmark.domain.SavedSpot
import com.chengjiguanjia.spotmark.domain.toDomain
import com.chengjiguanjia.spotmark.domain.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavedSpotRepository(
    private val dao: SavedSpotDao,
) {
    fun observeSpots(): Flow<List<SavedSpot>> =
        dao.observeSpots().map { spots -> spots.map { it.toDomain() } }

    fun observeSpot(id: Long): Flow<SavedSpot?> =
        dao.observeSpot(id).map { it?.toDomain() }

    suspend fun addSpot(
        title: String,
        note: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        photoPaths: List<String>,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            SavedSpot(
                title = title,
                note = note,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                createdAt = now,
                updatedAt = now,
                photoPaths = photoPaths,
            ).toEntity(),
        )
    }

    suspend fun updateSpot(spot: SavedSpot) {
        dao.update(spot.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteSpot(spot: SavedSpot) {
        dao.delete(spot.toEntity())
    }
}
