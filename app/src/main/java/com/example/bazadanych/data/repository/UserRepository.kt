package com.example.bazadanych.data.repository

import okhttp3.*
import java.io.IOException
import com.example.bazadanych.BuildConfig
import org.json.JSONObject

class UserRepository {

    private val client = OkHttpClient()
    private val BASE_URL = "https://rain-tech.pl"

    /* =========================
       LOGIN
    ========================= */
    fun loginUser(
        email: String,
        password: String,
        callback: (String?) -> Unit
    ) {
        val requestBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/login.php")
            .post(requestBody)
            .addHeader("X-APP-KEY", BuildConfig.APP_KEY)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.use { body ->
                    val rawBody = body.string()
                    println("RAW LOGIN RESPONSE: '$rawBody'") // debug

                    try {
                        val json = JSONObject(rawBody)
                        val status = json.optString("status")
                        if (status == "SUCCESS") {
                            val userId = json.optInt("user_id")
                            val emailResp = json.optString("email")
                            // Możesz tu złożyć własny "token", np. JSON string
                            val token = "$userId|$emailResp"
                            callback(token)
                        } else {
                            callback(null)
                        }
                    } catch (e: Exception) {
                        callback(null)
                    }
                } ?: run {
                    callback(null)
                }
            }
        })
    }

    /* =========================
       REGISTER
    ========================= */
    fun registerUser(
        email: String,
        password: String,
        callback: (String) -> Unit
    ) {
        println("APP_KEY: '${BuildConfig.APP_KEY}'")
        val requestBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/register.php")
            .post(requestBody)
            .addHeader("X-APP-KEY", BuildConfig.APP_KEY) // wysyłamy klucz
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                callback("NETWORK_ERROR")
            }

            override fun onResponse(call: Call, response: Response) {
                val rawBody = response.body?.string()
                println("RAW REGISTER RESPONSE: '$rawBody'") // <--- zobacz co przychodzi

                val bodyTrimmed = rawBody?.trim()?.uppercase()
                println("TRIMMED REGISTER RESPONSE: '$bodyTrimmed'")

                when (bodyTrimmed) {
                    "OK" -> callback("SUCCESS")
                    "EMAIL_EXISTS" -> callback("EMAIL_EXISTS")
                    "BAD_EMAIL" -> callback("BAD_EMAIL")
                    "BAD_PASSWORD" -> callback("BAD_PASSWORD")
                    "RATE_LIMIT" -> callback("RATE_LIMIT")
                    "REGISTER_ERROR" -> callback("REGISTER_ERROR")
                    "NO_APP_KEY" -> callback("NO_APP_KEY")
                    "INVALID_APP" -> callback("INVALID_APP")
                    else -> callback("UNKNOWN_ERROR")
                }
            }
        })
    }
}