package com.example.bazadanych.data.local_db

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CacheHelper {

    @PublishedApi
    internal const val PREFS_NAME = "AgroAppCache"

    @PublishedApi
    internal val gson = Gson()

    /**
     * Usuwa uszkodzony wpis z cache. Wywoływane, gdy Gson nie potrafi
     * odczytać zapisanych danych — zwykle po zmianie modelu (np. pole
     * isWorking było Boolean, a teraz jest Int).
     */
    @PublishedApi
    internal fun invalidate(context: Context, key: String, e: Exception) {
        Log.w("CacheHelper", "Uszkodzony cache pod kluczem '$key', usuwam. ${e.message}")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    }

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
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(json, type)
        } catch (e: Exception) {
            invalidate(context, key, e)
            null
        }
    }

    // 3. ZAPISYWANIE DATY OSTATNIEJ SYNCHRONIZACJI
    fun saveLastSyncTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        prefs.edit().putString("LAST_SYNC", sdf.format(Date())).apply()
    }

    // 4. POBIERANIE DATY OSTATNIEJ SYNCHRONIZACJI
    fun getLastSyncTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("LAST_SYNC", "Nigdy") ?: "Nigdy"
    }

    // 5. ZAPISYWANIE POJEDYNCZEGO OBIEKTU
    inline fun <reified T> saveObject(context: Context, key: String, obj: T) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(obj)
        prefs.edit().putString(key, json).apply()
    }

    // 6. ODCZYTYWANIE POJEDYNCZEGO OBIEKTU
    inline fun <reified T> loadObject(context: Context, key: String): T? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, T::class.java)
        } catch (e: Exception) {
            invalidate(context, key, e)
            null
        }
    }

    /** Ręczne czyszczenie pojedynczego klucza. */
    fun clear(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    }

    /** Czyszczenie całego cache — przydatne przy wylogowaniu. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    data class MapConfig(
        val lat: Double,
        val lng: Double,
        val zoom: Double
    ) : java.io.Serializable
}