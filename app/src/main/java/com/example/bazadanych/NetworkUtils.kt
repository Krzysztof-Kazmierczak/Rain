package com.example.bazadanych

import okhttp3.*
import java.io.IOException

object NetworkUtils {

    private val client = OkHttpClient()

    fun sendUser(email: String, password: String) {

        val requestBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("http://10.0.2.2:8080/raintech/save_user.php")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}