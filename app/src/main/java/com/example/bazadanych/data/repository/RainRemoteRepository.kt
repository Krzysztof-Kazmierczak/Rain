package com.example.bazadanych.data.repository

import com.example.bazadanych.data.db.Rain
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RainRemoteRepository {

    private val baseUrl = "https://rain-tech.pl/"

    fun getRains(email: String, callback: (List<Rain>) -> Unit) {
        Thread {
            try {
                val url = URL("${baseUrl}get_rains.php?email=${URLEncoder.encode(email, "UTF-8")}")
                val connection = url.openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()

                val array = JSONArray(response)
                val list = mutableListOf<Rain>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(Rain(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        hoseLength = obj.getString("hose_length"),
                        comment = obj.getString("comment")
                    ))
                }
                callback(list)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(emptyList())
            }
        }.start()
    }

    fun saveRain(email: String, rain: Rain, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL("${baseUrl}save_rain.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true

                val data = "id=${rain.id}&email=$email&name=${rain.name}&length=${rain.hoseLength}&comment=${rain.comment}"
                conn.outputStream.write(data.toByteArray())

                val res = conn.inputStream.bufferedReader().readText().trim()
                callback(res == "OK")
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun deleteRain(id: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL("${baseUrl}delete_rain.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.write("id=$id".toByteArray())
                callback(conn.inputStream.bufferedReader().readText().trim() == "OK")
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }
}