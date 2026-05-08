package com.example.bazadanych.data.api

import com.example.bazadanych.data.db.FieldHistory // Zakładam, że tu masz model FieldHistory
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
        @Query("email") email: String // <--- DODAJ TO
    ): Call<FieldHistory>

    @GET("get_history.php")
    fun getFieldHistory(
        @Query("field_id") fieldId: Int,
        @Query("email") email: String // <--- Tutaj pewnie też będzie potrzebny
    ): Call<List<FieldHistory>>
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