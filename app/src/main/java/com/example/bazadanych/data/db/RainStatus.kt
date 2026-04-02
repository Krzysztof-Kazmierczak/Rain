package com.example.bazadanych.data.db

data class RainStatus(
    val id: Int = 0,
    val rainId: String,
    val isWorking: Boolean,
    val setSpeed: Double,
    val currentSpeed: Double,
    val currentDistance: Double,
    val timeToFinish: String,
    val updatedAt: String = ""
)