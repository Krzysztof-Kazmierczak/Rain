package com.example.bazadanych.data.calculation

import com.example.bazadanych.data.model.ForecastItem

object WeatherAdvisor {

    // Funkcja pobiera listę prognoz (co 3h) i analizuje najbliższe 8 wyników (czyli 24 godziny)
    fun generateWateringAdvice(forecastList: List<ForecastItem>): AdviceResult {
        // Zliczamy sumę opadów z najbliższych 24h
        val rainInNext24h = forecastList.take(8).sumOf { it.rain?.threeHours ?: 0.0 }
        val maxTempInNext24h = forecastList.take(8).maxOfOrNull { it.main.temp } ?: 0.0

        return when {
            rainInNext24h > 5.0 -> AdviceResult(
                "🚫 NIE PODLEWAJ",
                "Prognozowane silne opady: ${String.format("%.1f", rainInNext24h)} mm. Natura zrobi to za Ciebie!",
                "#F44336" // Czerwony
            )
            rainInNext24h > 0.5 -> AdviceResult(
                "⚠️ WSTRZYMAJ SIĘ",
                "Przewidywane lekkie opady (${String.format("%.1f", rainInNext24h)} mm). Obserwuj sytuację.",
                "#FF9800" // Pomarańczowy
            )
            maxTempInNext24h > 25.0 -> AdviceResult(
                "💧 ZALECANE PODLEWANIE WIECZOREM",
                "Brak opadów i wysoka temperatura (${String.format("%.1f", maxTempInNext24h)}°C) przyspieszy parowanie.",
                "#2196F3" // Niebieski
            )
            else -> AdviceResult(
                "✅ MOŻNA PODLEWAĆ",
                "Brak opadów w prognozie, warunki umiarkowane.",
                "#4CAF50" // Zielony
            )
        }
    }

    data class AdviceResult(val title: String, val message: String, val colorCode: String)
}