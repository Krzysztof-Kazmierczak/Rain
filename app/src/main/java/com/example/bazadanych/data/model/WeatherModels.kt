package com.example.bazadanych.data.model // Pamiętaj o podmianie na swój folder, jeśli jest inny!

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val list: List<ForecastItem>
)

data class ForecastItem(
    val main: MainData,
    val weather: List<WeatherData>,
    val rain: RainData?,     // Może być null, jeśli nie pada
    val dt_txt: String       // Data i czas prognozy
)

data class MainData(
    val temp: Double,
    val humidity: Int
)

data class WeatherData(
    val description: String,
    val icon: String
)

data class RainData(
    @SerializedName("3h") val threeHours: Double? // Ilość opadu w ciągu 3 godzin (w mm)
)