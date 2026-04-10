package com.example.bazadanych.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


@Dao
interface RainDao {
    @Query("SELECT * FROM rain_history ORDER BY date DESC")
    fun getAllHistory(): Flow<List<RainEntity>> // Flow automatycznie odświeży UI przy zmianach

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rainData: List<RainEntity>)

    @Query("DELETE FROM rain_history")
    suspend fun clearAll()
}