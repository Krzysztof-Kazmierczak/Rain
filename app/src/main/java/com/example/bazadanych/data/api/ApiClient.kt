package com.example.bazadanych.data.api

import com.example.bazadanych.data.db.DeviceItem // Upewnij się, że ten import (db czy model?) jest poprawny
import com.example.bazadanych.data.db.DeviceTelemetry
import com.example.bazadanych.data.db.FieldHistory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// 1. Interfejs do Twojego serwera na home.pl
interface RainTechApi {
    @GET("get_current_weather.php")
    fun getCurrentWeather(
        @Query("field_id") fieldId: Int,
        @Query("email") email: String
    ): Call<FieldHistory>

    @GET("get_history.php")
    fun getFieldHistory(
        @Query("field_id") fieldId: Int,
        @Query("email") email: String
    ): Call<List<FieldHistory>>

    // Zakładam, że stworzyłeś skrypt get_devices.php do pobrania maszyn
    @GET("get_devices.php")
    fun getDevices(
        @Query("email") email: String
    ): Call<List<DeviceItem>>

    // Zakładam, że stworzyłeś skrypt get_telemetry.php do historii
    // Przerobiłem @Path na @Query, bo w zwykłym PHP łatwiej to odebrać jako $_GET['device_id']
    @GET("get_telemetry.php")
    fun getDeviceTelemetry(
        @Query("device_id") deviceId: Int,
        @Query("email") email: String
    ): Call<List<DeviceTelemetry>>
}

// 2. Klient uderzający do home.pl
object ApiClient {
    private const val BASE_URL = "https://rain-tech.pl/android/"

    val rainTech: RainTechApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RainTechApi::class.java)
    }
}