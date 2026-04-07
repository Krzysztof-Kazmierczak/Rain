package com.example.bazadanych.data.api

import com.example.bazadanych.data.db.FieldHistory // Zakładam, że tu masz model FieldHistory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// 1. Interfejs do Twojego serwera na home.pl
interface RainTechApi {

    // Pobieranie aktualnej pogody z Twojej bazy
    @GET("/get_current_weather.php") // UWAGA: upewnij się, że ścieżka na serwerze jest taka sama!
    fun getCurrentWeather(@Query("field_id") fieldId: Int): Call<FieldHistory>

    // Pobieranie historii do wykresów
    @GET("/get_history.php") // UWAGA: upewnij się, że ścieżka na serwerze jest taka sama!
    fun getFieldHistory(@Query("field_id") fieldId: Int): Call<List<FieldHistory>>
}

// 2. Klient uderzający do home.pl
object ApiClient {
    private const val BASE_URL = "https://rain-tech.pl/"

    val rainTech: RainTechApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RainTechApi::class.java)
    }
}