package com.example.bazadanych.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rain_history")
data class RainEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val amount: Double,
    val latitude: Double,
    val longitude: Double,
    val note: String?
)