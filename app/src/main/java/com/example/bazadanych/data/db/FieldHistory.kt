package com.example.bazadanych.data.db

data class FieldHistory(
    val id: Int,
    val field_id: Int,
    val temperature: Double?,
    val rain_mm: Double?,
    val machine_speed: Double?,
    val humidity: Int?,
    val wind_speed: Double?,
    val wind_deg: Int?,
    val pressure: Int?,
    val clouds: Int?,
    val is_forecast: Int?,
    val recorded_at: String?,
    val created_at: String?
)

// 1. Z API do bazy danych (Room)
fun FieldHistory.toEntity(fId: Int): FieldEntity {
    return FieldEntity(
        fieldId = fId,
        temperature = this.temperature ?: 0.0,
        rainMm = this.rain_mm ?: 0.0,
        machineSpeed = this.machine_speed,
        humidity = this.humidity ?: 0,
        windSpeed = this.wind_speed ?: 0.0,
        windDeg = this.wind_deg ?: 0,
        pressure = this.pressure ?: 0,
        clouds = this.clouds ?: 0,
        isForecast = this.is_forecast ?: 0,
        recordedAt = this.recorded_at ?: ""
    )
}

// 2. Z bazy danych (Room) powrót do modelu wyświetlanego na wykresach
fun FieldEntity.toDomainModel(): FieldHistory {
    return FieldHistory(
        id = this.localId,
        field_id = this.fieldId,
        temperature = this.temperature,
        rain_mm = this.rainMm,
        machine_speed = this.machineSpeed,
        humidity = this.humidity,
        wind_speed = this.windSpeed,
        wind_deg = this.windDeg,
        pressure = this.pressure,
        clouds = this.clouds,
        is_forecast = this.isForecast,
        recorded_at = this.recordedAt,
        created_at = null // Z bazy nie potrzebujemy już daty utworzenia
    )
}