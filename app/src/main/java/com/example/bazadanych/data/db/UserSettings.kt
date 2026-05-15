package com.example.bazadanych.data.db

data class UserSettings(
    val powiadomienia: Boolean,
    val powiadomienie_A: Boolean,
    val powiadomienie_B: Boolean,
    val powiadomienie_C: Boolean,
    val sms: Boolean,
    val dzwonienie: Boolean
)