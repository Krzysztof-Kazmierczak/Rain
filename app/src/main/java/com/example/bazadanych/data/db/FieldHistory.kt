package com.example.bazadanych.data.db

data class FieldHistory(
    val id: Int,
    val field_id: Int,
    val temperature: Double,
    val rain_mm: Double,
    val recorded_at: String,
    val created_at: String  // DODAJ TO POLE TUTAJ
)