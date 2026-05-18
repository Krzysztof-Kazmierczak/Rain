package com.example.bazadanych.data.db

data class AlarmItem(
    val id: Int,
    val kodAlarmu: String,
    val nazwaAlarmu: String,
    val nazwaMaszyny: String,
    val dataWystapienia: String,
    val userEmail: String,
    val canDelete: Boolean // <--- Nowe pole z bazy danych
)