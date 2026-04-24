package com.example.bazadanych.data.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bazadanych.data.db.FieldData
import com.example.bazadanych.data.db.FieldItem
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.db.RainStatus
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RainRemoteRepository {

    private val baseUrl = "https://rain-tech.pl/android/"
    private val client = OkHttpClient()
    private val TAG = "RainRepo"
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun postOnMain(action: () -> Unit) = mainHandler.post(action)

    fun getAgriculturalFields(email: String, callback: (List<FieldItem>) -> Unit) {
        val url = "${baseUrl}get_agricultural_fields.php?email=$email"
        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { postOnMain { callback(emptyList()) } }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val list = mutableListOf<FieldItem>()
                try {
                    val jsonArray = JSONArray(body ?: "[]")
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(FieldItem(
                            id = obj.optInt("id", 0),
                            name = obj.optString("name", "Pole"),
                            areaHa = obj.optDouble("area_ha", 0.0),
                            cropType = obj.optString("crop_type", ""),
                            comment = obj.optString("comment", ""),
                            color = obj.optString("color", "#604CAF50"),
                            coordinates = obj.optString("coordinates", "")
                        ))
                    }
                } catch (e: Exception) { }
                postOnMain { callback(list) }
            }
        })
    }

    fun getRains(email: String, callback: (List<Rain>) -> Unit) {
        val url = "${baseUrl}get_rains_with_status.php?email=$email"
        Log.d(TAG, "Pobieram maszyny z URL: $url") // DODAJ TO

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Błąd sieci: ${e.message}")
                postOnMain { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                Log.d(TAG, "Odpowiedź serwera: $jsonResponse") // DODAJ TO

                val list = mutableListOf<Rain>()
                if (!jsonResponse.isNullOrEmpty()) {
                    try {
                        val jsonArray = JSONArray(jsonResponse)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            list.add(Rain(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", "Maszyna"),
                                hoseLength = obj.optString("hose_length", "0"),
                                comment = obj.optString("comment", ""),
                                isWorking = obj.optInt("is_working", 0) == 1
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Błąd parsowania: ${e.message}")
                    }
                }
                postOnMain { callback(list) }
            }
        })
    }

    fun getRainDetails(id: String, email: String, callback: (name: String, length: String, comment: String) -> Unit) {
        val url = "${baseUrl}get_rain_details.php?id=$id&email=$email"
        Log.d("RainRepoDetails", "---> Wywołuję URL: $url")

        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RainRepoDetails", "Błąd połączenia: ${e.message}")
                postOnMain { callback("", "", "") }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d("RainRepoDetails", "<--- Odpowiedź serwera: $body")

                try {
                    if (body.isNullOrEmpty() || body.contains("error")) {
                        Log.e("RainRepoDetails", "Serwer zwrócił błąd lub pustkę!")
                        postOnMain { callback("", "", "") }
                        return
                    }

                    val obj = JSONObject(body)
                    postOnMain {
                        callback(
                            obj.optString("name", ""),
                            obj.optString("hose_length", "0"),
                            obj.optString("comment", "")
                        )
                    }
                } catch (e: Exception) {
                    Log.e("RainRepoDetails", "Błąd parsowania JSON: ${e.message}")
                    postOnMain { callback("", "", "") }
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

    fun getRainHistory(rainId: String, email: String, callback: (List<RainStatus>) -> Unit) {
        // Dodajemy email do adresu URL
        val url = "${baseUrl}get_rain_history.php?rain_id=$rainId&email=$email"

        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postOnMain { callback(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val list = mutableListOf<RainStatus>()
                try {
                    val jsonArray = JSONArray(body ?: "[]")
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(RainStatus(
                            id = obj.optInt("id"),
                            rainId = obj.optString("rain_id"),
                            isWorking = obj.optBoolean("is_working"),
                            setSpeed = obj.optDouble("set_speed"),
                            currentSpeed = obj.optDouble("current_speed"),
                            currentDistance = obj.optDouble("current_distance"),
                            extension = obj.optDouble("extension"), // Pobieranie nowej danej
                            workTime = obj.optString("work_time", "00:00:00"), // Pobieranie nowej danej
                            timeToFinish = obj.optString("time_to_finish", "--:--"),
                            updatedAt = obj.optString("updated_at"),
                            lat = obj.optDouble("lat"),
                            lng = obj.optDouble("lng"),
                            signalStrength = obj.optInt("signal")
                        ))
                    }
                } catch (e: Exception) { e.printStackTrace() }
                postOnMain { callback(list) }
            }
        })
    }
    fun saveAgriculturalField(
        email: String, id: String, name: String, areaHa: Double,
        cropType: String, comment: String, color: String, coords: String,
        callback: (Boolean) -> Unit
    ) {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("id", id)
            .add("name", name)
            .add("area_ha", areaHa.toString())
            .add("crop_type", cropType)
            .add("comment", comment)
            .add("color", color)
            .add("coordinates", coords)
            .build()

        val request = Request.Builder()
            .url(baseUrl + "save_agricultural_field.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
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

    fun updateRainManualLocation(id: String, email: String, lat: Double, lng: Double, callback: (Boolean) -> Unit) {
        val url = baseUrl + "update_rain_location.php"

        // LOG 1: Sprawdzamy co wysyłamy
        Log.d("RainRepoDebug", "===> WYSYŁAM LOKALIZACJĘ:")
        Log.d("RainRepoDebug", "URL: $url")
        Log.d("RainRepoDebug", "ID: $id, Email: $email, Lat: $lat, Lng: $lng")

        val formBody = FormBody.Builder()
            .add("id", id)
            .add("email", email)
            .add("lat", lat.toString())
            .add("lng", lng.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // LOG 2: Błąd połączenia (np. brak internetu, zły URL)
                Log.e("RainRepoDebug", "!!! BŁĄD SIECI: ${e.message}")
                postOnMain { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()?.trim()

                // LOG 3: Surowa odpowiedź z serwera
                Log.d("RainRepoDebug", "<=== ODPOWIEDŹ SERWERA: '$responseBody'")

                if (responseBody == "OK") {
                    postOnMain { callback(true) }
                } else {
                    // LOG 4: Serwer odpisał, ale coś mu się nie spodobało
                    Log.w("RainRepoDebug", "Serwer nie zwrócił OK. Treść: $responseBody")
                    postOnMain { callback(false) }
                }
            }
        })
    }


    fun deleteAgriculturalField(email: String, id: String, callback: (Boolean) -> Unit) {
        val formBody = FormBody.Builder().add("email", email).add("id", id).build()
        val request = Request.Builder().url(baseUrl + "delete_agricultural_field.php").post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { postOnMain { callback(false) } }
            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                postOnMain { callback(result == "OK") }
            }
        })
    }
}