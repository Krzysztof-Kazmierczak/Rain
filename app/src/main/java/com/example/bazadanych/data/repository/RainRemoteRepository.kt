package com.example.bazadanych.data.repository

import android.util.Log
import com.example.bazadanych.data.db.FieldData
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.db.RainStatus
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RainRemoteRepository {

    // Dodany ukośnik na końcu!
    private val baseUrl = "https://rain-tech.pl/"
    private val client = OkHttpClient()
    private val TAG = "RainRepo"

    fun saveField(email: String, rainId: String, color: String, coords: String, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("rain_id", rainId)
            .add("color", color)
            .add("coordinates", coords)
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_field.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "saveField - Błąd połączenia: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                Log.d(TAG, "saveField - Odpowiedź serwera: $result")
                callback(result == "OK")
            }
        })
    }

    fun getField(email: String, rainId: String, callback: (FieldData?) -> Unit) {
        val url = "${baseUrl}get_field.php?email=$email&rain_id=$rainId"

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "getField - Błąd połączenia: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                Log.d(TAG, "getField - Odpowiedź: $jsonResponse")
                if (jsonResponse != null) {
                    try {
                        val json = JSONObject(jsonResponse)
                        if (json.getString("status") == "OK") {
                            val fieldData = FieldData(
                                color = json.getString("color"),
                                coordinates = json.getString("coordinates")
                            )
                            callback(fieldData)
                        } else {
                            callback(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getField - Błąd parsowania JSON: ${e.message}")
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
        })
    }

    fun getRains(email: String, callback: (List<Rain>) -> Unit) {
        val url = "${baseUrl}get_rains_with_status.php?email=$email"
        val request = Request.Builder().url(url).get().build()

        Log.d(TAG, "getRains - Pobieranie listy z URL: $url")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "getRains - Błąd połączenia: ${e.message}")
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                Log.d(TAG, "getRains - Odpowiedź serwera: $jsonResponse")

                val list = mutableListOf<Rain>()
                if (!jsonResponse.isNullOrEmpty()) {
                    try {
                        val jsonArray = JSONArray(jsonResponse)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(Rain(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", "Brak nazwy"),
                                hoseLength = obj.optString("hose_length", "0"),
                                comment = obj.optString("comment", ""),
                                isWorking = obj.optInt("is_working", 0) == 1
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getRains - Błąd parsowania JSON: ${e.message}")
                    }
                }
                callback(list)
            }
        })
    }

    fun getRainDetails(id: String, callback: (Rain?) -> Unit) {
        val url = "${baseUrl}get_rain_details.php?id=$id"
        val request = Request.Builder().url(url).get().build()

        Log.d(TAG, "Pobieranie detali z URL: $url")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "getRainDetails - Błąd połączenia: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                Log.d(TAG, "getRainDetails - Odpowiedź serwera: $jsonResponse")

                if (jsonResponse == "ERROR" || jsonResponse.isNullOrEmpty()) {
                    callback(null)
                } else {
                    try {
                        val obj = JSONObject(jsonResponse)
                        val rain = Rain(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            hoseLength = obj.getString("hose_length"),
                            comment = obj.getString("comment"),
                            isWorking = false
                        )
                        callback(rain)
                    } catch (e: Exception) {
                        Log.e(TAG, "getRainDetails - Błąd parsowania: ${e.message}")
                        callback(null)
                    }
                }
            }
        })
    }

    fun saveRain(email: String, rain: Rain, callback: (Boolean) -> Unit) {
        val finalId = if (rain.id.isEmpty()) "0" else rain.id

        val formBody = FormBody.Builder()
            .add("id", finalId)
            .add("name", rain.name)
            .add("length", rain.hoseLength)
            .add("comment", rain.comment)
            .add("email", email)
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_rain.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "saveRain - Błąd połączenia: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                Log.d(TAG, "saveRain - Odpowiedź: $result")
                callback(result == "OK")
            }
        })
    }

    fun deleteRain(id: String, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder().add("id", id).build()
        val request = Request.Builder().url(baseUrl + "delete_rain.php").post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                callback(result == "OK")
            }
        })
    }

    fun getRainHistory(rainId: String, callback: (List<RainStatus>) -> Unit) {
        val url = "${baseUrl}get_rain_history.php?rain_id=$rainId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(emptyList()) }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                val list = mutableListOf<RainStatus>()
                try {
                    val jsonArray = JSONArray(jsonResponse)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(RainStatus(
                            id = obj.getInt("id"),
                            rainId = obj.getString("rain_id"),
                            isWorking = obj.optBoolean("is_working", false),
                            setSpeed = obj.getDouble("set_speed"),
                            currentSpeed = obj.getDouble("current_speed"),
                            currentDistance = obj.getDouble("current_distance"),
                            timeToFinish = obj.getString("time_to_finish"),
                            updatedAt = obj.getString("updated_at"),
                            lat = obj.getDouble("lat"),
                            lng = obj.getDouble("lng")
                        ))
                    }
                    callback(list)
                } catch (e: Exception) {
                    callback(emptyList())
                }
            }
        })
    }
}