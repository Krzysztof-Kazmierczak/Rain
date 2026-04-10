package com.example.bazadanych.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "field_history")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0, // Unikalne ID dla Room
    val fieldId: Int,
    val temperature: Double,
    val rainMm: Double,
    val machineSpeed: Double?,
    val humidity: Int,
    val windSpeed: Double,
    val windDeg: Int,
    val pressure: Int,
    val clouds: Int,
    val isForecast: Int,
    val recordedAt: String
)