package com.example.bazadanych.data.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

object RainStorage {

    private const val PREF_NAME = "rain_storage"
    private const val KEY_RAINS = "rains"

    fun saveRain(context: Context, rain: Rain) {
        val rains = loadRains(context).toMutableList()

        // jeśli już istnieje rain z tym ID → nadpisz
        val index = rains.indexOfFirst { it.id == rain.id }
        if (index >= 0) {
            rains[index] = rain
        } else {
            rains.add(rain)
        }

        saveAll(context, rains)
    }

    fun deleteRain(context: Context, rainId: String) {
        val rains = loadRains(context).filter { it.id != rainId }
        saveAll(context, rains)
    }

    private fun saveAll(context: Context, rains: List<Rain>) {
        val array = JSONArray()
        rains.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("length", it.hoseLength)
            obj.put("comment", it.comment)
            array.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_RAINS, array.toString())
            }
    }

    fun loadRains(context: Context): List<Rain> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RAINS, null)
        if (json.isNullOrEmpty()) return emptyList()

        val array = JSONArray(json)
        val list = mutableListOf<Rain>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            list.add(
                Rain(
                    id = obj.getString("id"),        // 🔹 przywracamy ID
                    name = obj.getString("name"),
                    hoseLength = obj.getString("length"),
                    comment = obj.getString("comment")
                )
            )
        }

        return list
    }
}