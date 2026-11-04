package com.example.bazadanych.data.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bazadanych.data.db.PhoneNumberItem
import com.example.bazadanych.data.db.PhoneNumbersResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Numery telefonów przypisane do maszyny.
 * Uprawnienia sprawdza serwer; aplikacja tylko ukrywa przyciski.
 */
class PhoneNumberRepository {

    private val baseUrl = "https://rain-tech.pl/web/"
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tag = "PhoneRepo"

    fun getNumbers(machineId: String, email: String, callback: (PhoneNumbersResponse?) -> Unit) {
        val url = "${baseUrl}get_phone_numbers.php".toHttpUrl().newBuilder()
            .addQueryParameter("id", machineId)
            .addQueryParameter("email", email)
            .build()

        client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(tag, "Błąd sieci: ${e.message}")
                mainHandler.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.use { it.body?.string() }
                val parsed = try {
                    parseNumbers(body)
                } catch (e: Exception) {
                    Log.e(tag, "Błąd parsowania: ${e.message}, odpowiedź: $body")
                    null
                }
                mainHandler.post { callback(parsed) }
            }
        })
    }

    private fun parseNumbers(body: String?): PhoneNumbersResponse? {
        if (body.isNullOrBlank()) return null
        val obj = JSONObject(body)
        if (obj.has("error")) {
            Log.e(tag, "Serwer zwrócił błąd: ${obj.optString("error")}")
            return null
        }

        val list = mutableListOf<PhoneNumberItem>()
        val arr = obj.optJSONArray("numbers")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                list.add(
                    PhoneNumberItem(
                        id = n.optInt("id"),
                        kierunkowy = n.optString("kierunkowy", "48"),
                        numer = n.optString("numer"),
                        poziomDostepu = n.optInt("poziom_dostepu", 3),
                        zgodaSms = n.optInt("zgoda_sms") == 1,
                        potwierdzono = n.optString("potwierdzono")
                            .takeIf { it.isNotEmpty() && it != "null" },
                        isAdminNumber = n.optBoolean("is_admin_number", false)
                    )
                )
            }
        }

        return PhoneNumbersResponse(
            canManage = obj.optBoolean("can_manage", false),
            myLevel = obj.optInt("my_level", -1),
            adminNumber = obj.optString("admin_number", ""),
            numbers = list
        )
    }

    /** id = 0 dodaje nowy numer, inne id edytuje istniejący. */
    fun saveNumber(
        id: Int,
        machineId: String,
        email: String,
        kierunkowy: String,
        numer: String,
        poziomDostepu: Int,
        callback: (success: Boolean, message: String?) -> Unit
    ) {
        val form = FormBody.Builder()
            .add("id", id.toString())
            .add("urzadzenie_id", machineId)
            .add("email", email)
            .add("kierunkowy", kierunkowy)
            .add("numer", numer)
            .add("poziom_dostepu", poziomDostepu.toString())
            .build()
        post("save_phone_number.php", form, callback)
    }

    fun deleteNumber(
        id: Int,
        machineId: String,
        email: String,
        callback: (success: Boolean, message: String?) -> Unit
    ) {
        val form = FormBody.Builder()
            .add("id", id.toString())
            .add("urzadzenie_id", machineId)
            .add("email", email)
            .build()
        post("delete_phone_number.php", form, callback)
    }

    /** message == null oznacza błąd sieci, w innym razie to odpowiedź serwera. */
    private fun post(
        script: String,
        form: FormBody,
        callback: (success: Boolean, message: String?) -> Unit
    ) {
        val request = Request.Builder().url(baseUrl + script).post(form).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(tag, "Błąd sieci ($script): ${e.message}")
                mainHandler.post { callback(false, null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use { it.body?.string()?.trim() } ?: ""
                Log.d(tag, "$script -> $result")
                mainHandler.post { callback(result == "OK", result) }
            }
        })
    }
}