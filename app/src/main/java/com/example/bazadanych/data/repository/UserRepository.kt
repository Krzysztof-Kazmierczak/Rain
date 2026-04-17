package com.example.bazadanych.data.repository

import java.net.HttpURLConnection
import java.net.URL

class UserRepository {

    // ================= LOGIN =================
    fun loginUser(
        email: String,
        password: String,
        callback: (Boolean) -> Unit
    ) {

        Thread {

            try {
                val url = URL("https://rain-tech.pl/android/login.php")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true

                val data = "email=$email&password=$password"

                connection.outputStream.write(data.toByteArray())

                val response =
                    connection.inputStream.bufferedReader()
                        .readText()
                        .trim()

                callback(response == "OK")

            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }

        }.start()
    }

    // ================= REGISTER =================
    fun registerUser(
        email: String,
        password: String,
        callback: (String) -> Unit
    ) {

        Thread {

            val url = URL("https://rain-tech.pl/android/register.php")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true

            val data = "email=$email&password=$password"
            connection.outputStream.write(data.toByteArray())

            val response =
                connection.inputStream.bufferedReader().readText().trim()

            callback(response)

        }.start()
    }
}