package com.example.bazadanych.data.db

// To jest model dla MAPY (nie dla Room, chyba że też chcesz to zapisać w bazie)
data class FieldItem(
    val id: Int,
    val name: String,
    val areaHa: Double,
    val cropType: String,
    val comment: String,
    val color: String,
    val coordinates: String
)

