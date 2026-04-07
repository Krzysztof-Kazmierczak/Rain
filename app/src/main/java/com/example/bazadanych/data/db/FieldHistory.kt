package com.example.bazadanych.data.db

data class FieldHistory(
    val id: Int,
    val temperature: Double,
    val rain_mm: Double,
    val machine_speed: Double,
    val soil_moisture: Double,
    val recorded_at: String // Format: "2023-10-27 12:00:00"
)