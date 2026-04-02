package com.example.bazadanych.data.repository

import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.db.RainStatus
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RainRemoteRepository {

    private val baseUrl = "https://rain-tech.pl"

    fun getRains(email: String, callback: (List<Rain>) -> Unit) {
        Thread {
            try {
                val url = URL("$baseUrl/get_rains_with_status.php?email=" + URLEncoder.encode(email, "UTF-8"))
                val conn = url.openConnection() as HttpURLConnection
                val response = conn.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(response)
                val list = mutableListOf<Rain>()

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
                callback(list)
            } catch (e: Exception) {
                e.printStackTrace() // Warto zostawić printa do debugowania w konsoli
                callback(emptyList())
            }
        }.start()
    }

    fun getRainDetails(id: String, callback: (Rain?) -> Unit) {
        Thread {
            try {
                val url = URL("$baseUrl/get_rain_details.php?id=" + URLEncoder.encode(id, "UTF-8"))
                val conn = url.openConnection() as HttpURLConnection
                val response = conn.inputStream.bufferedReader().readText()
                if (response == "ERROR") callback(null)
                else {
                    val obj = JSONObject(response)
                    // 👇 Tutaj dodaliśmy piąty parametr: false
                    callback(Rain(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("hose_length"),
                        obj.getString("comment"),
                        false
                    ))
                }
            } catch (e: Exception) { callback(null) }
        }.start()
    }

    fun saveRain(email: String, rain: Rain, callback: (Boolean) -> Unit) {
        Thread {
            try {
                // Jeśli rain.id jest puste, wysyłamy "0", żeby PHP wiedziało, że to nowa maszyna
                val finalId = if (rain.id.isEmpty()) "0" else rain.id

                val urlString = "$baseUrl/save_rain.php?" +
                        "id=${finalId}&" +
                        "name=${URLEncoder.encode(rain.name, "UTF-8")}&" +
                        "length=${URLEncoder.encode(rain.hoseLength, "UTF-8")}&" +
                        "comment=${URLEncoder.encode(rain.comment, "UTF-8")}&" +
                        "email=${URLEncoder.encode(email, "UTF-8")}"

                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                val response = conn.inputStream.bufferedReader().readText()

                // Jeśli PHP wypisze "OK", zwracamy sukces
                callback(response.trim() == "OK")
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }

    fun deleteRain(id: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL("$baseUrl/delete_rain.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.write("id=$id".toByteArray())
                callback(conn.inputStream.bufferedReader().readText() == "OK")
            } catch (e: Exception) { callback(false) }
        }.start()
    }

    // --- NOWE: OPERACJE NA STATUSIE I HISTORII ---

    fun updateRainStatus(status: RainStatus, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL("$baseUrl/update_rain_status.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                val workingInt = if (status.isWorking) 1 else 0
                val data = "rain_id=${status.rainId}&is_working=$workingInt&set_speed=${status.setSpeed}" +
                        "&current_speed=${status.currentSpeed}&current_distance=${status.currentDistance}" +
                        "&time_to_finish=${status.timeToFinish}"
                conn.outputStream.write(data.toByteArray())
                callback(conn.inputStream.bufferedReader().readText() == "OK")
            } catch (e: Exception) { callback(false) }
        }.start()
    }

    fun getRainHistory(rainId: String, callback: (List<RainStatus>) -> Unit) {
        Thread {
            try {
                val url = URL("$baseUrl/get_rain_history.php?rain_id=" + URLEncoder.encode(rainId, "UTF-8"))
                val conn = url.openConnection() as HttpURLConnection
                val response = conn.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(response)
                val list = mutableListOf<RainStatus>()
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
                        updatedAt = obj.getString("updated_at")
                    ))
                }
                callback(list)
            } catch (e: Exception) { callback(emptyList()) }
        }.start()
    }
}