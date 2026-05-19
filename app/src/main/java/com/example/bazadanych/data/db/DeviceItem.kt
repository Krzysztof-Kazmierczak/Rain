package com.example.bazadanych.data.db

import com.google.gson.annotations.SerializedName


data class DeviceItem(
    val id: Int,
    val user_email: String,
    val name: String,
    val hose_length: String?, // '?' bo w bazie może być NULL
    val comment: String?      // '?' bo w bazie może być NULL
)

data class DeviceTelemetry(
    val id: Int,
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("current_speed") val currentSpeed: Float?, // Prędkość aktualna
    @SerializedName("target_speed") val targetSpeed: Float?,   // Prędkość zadana
    val distance: Float?,                                               // Odległość
    @SerializedName("work_time") val workTime: Float?,         // Czas pracy
    val battery: Float?,                                                 // Bateria
    @SerializedName("sim_signal") val simSignal: Float?,       // Sygnał SIM
    @SerializedName("recorded_at") val recordedAt: String      // Format: yyyy-MM-dd HH:mm:ss
)