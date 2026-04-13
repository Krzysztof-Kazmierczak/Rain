package com.example.bazadanych.data.local_db

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CacheHelper {

    // Zmieniamy na @PublishedApi internal, aby funkcje inline miały do nich dostęp
    @PublishedApi
    internal const val PREFS_NAME = "AgroAppCache"

    @PublishedApi
    internal val gson = Gson()

    // 1. ZAPISYWANIE LISTY DANYCH
    inline fun <reified T> saveList(context: Context, key: String, list: List<T>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(key, json).apply()
    }

    // 2. ODCZYTYWANIE LISTY DANYCH
    inline fun <reified T> loadList(context: Context, key: String): List<T>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key, null) ?: return null
        val type = object : TypeToken<List<T>>() {}.type
        return gson.fromJson(json, type)
    }

    // 3. ZAPISYWANIE DATY OSTATNIEJ SYNCHRONIZACJI
    fun saveLastSyncTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val now = sdf.format(Date())
        prefs.edit().putString("LAST_SYNC", now).apply()
    }

    // 4. POBIERANIE DATY OSTATNIEJ SYNCHRONIZACJI
    fun getLastSyncTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("LAST_SYNC", "Nigdy") ?: "Nigdy"
    }

    // 5. ZAPISYWANIE POJEDYNCZEGO OBIEKTU (Dopisujemy to do Twojego CacheHelper)
    inline fun <reified T> saveObject(context: Context, key: String, obj: T) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(obj)
        prefs.edit().putString(key, json).apply()
    }

    // 6. ODCZYTYWANIE POJEDYNCZEGO OBIEKTU (Dopisujemy to do Twojego CacheHelper)
    inline fun <reified T> loadObject(context: Context, key: String): T? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, T::class.java)
        } catch (e: Exception) {
            null
        }
    }
}