package com.example.bazadanych.ui

data class RainTile(
    val id: String = "",      // dodajemy ID
    val title: String,
    val hoseLength: String = "",
    val comment: String = "",
    val isAddButton: Boolean = false
)