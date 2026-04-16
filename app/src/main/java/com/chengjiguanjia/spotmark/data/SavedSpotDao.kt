package com.chengjiguanjia.spotmark.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSpotDao {
    @Query("SELECT * FROM saved_spots ORDER BY updatedAt DESC")
    fun observeSpots(): Flow<List<SavedSpotEntity>>

    @Query("SELECT * FROM saved_spots WHERE id = :id")
    fun observeSpot(id: Long): Flow<SavedSpotEntity?>

    @Insert
    suspend fun insert(spot: SavedSpotEntity): Long

    @Update
    suspend fun update(spot: SavedSpotEntity)

    @Delete
    suspend fun delete(spot: SavedSpotEntity)
}
