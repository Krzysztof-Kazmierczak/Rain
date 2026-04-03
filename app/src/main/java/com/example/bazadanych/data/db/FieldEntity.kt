package com.example.bazadanych.data.db

data class FieldEntity(
    val id: Int,
    val name: String,
    val areaHa: Double,
    val cropType: String,
    val comment: String,
    val color: String,
    val coordinates: String
)