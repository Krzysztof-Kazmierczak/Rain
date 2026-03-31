package com.example.bazadanych.data.db

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RainStorage {

    private const val PREF_NAME = "rain_storage"
    private const val KEY_RAINS = "rains"

    fun saveRain(context: Context, rain: Rain) {

        val rains = loadRains(context).toMutableList()
        rains.add(rain)

        val array = JSONArray()

        rains.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("length", it.hoseLength)
            obj.put("comment", it.comment)
            array.put(obj)
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RAINS, array.toString())
            .apply()
    }

    fun loadRains(context: Context): List<Rain> {

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RAINS, null) ?: return emptyList()

        val array = JSONArray(json)
        val list = mutableListOf<Rain>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            list.add(
                Rain(
                    obj.getString("name"),
                    obj.getString("length"),
                    obj.getString("comment")
                )
            )
        }

        return list
    }
}