package com.example.bazadanych.data.db

data class FieldHistory(
    val id: Int,
    val field_id: Int,
    val temperature: Double?,
    val rain_mm: Double?,
    val machine_speed: Double?,
    // NOWE POLA Z BAZY:
    val humidity: Int?,
    val wind_speed: Double?,
    val wind_deg: Int?,
    val pressure: Int?,
    val clouds: Int?,
    val is_forecast: Int?, // 0 = przeszłość, 1 = prognoza
    val recorded_at: String? // Używaj recorded_at zamiast created_at (wg skryptu PHP)
)