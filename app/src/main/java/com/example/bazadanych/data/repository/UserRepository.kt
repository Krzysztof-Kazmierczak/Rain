package com.example.bazadanych.data.repository

import com.example.bazadanych.data.network.NetworkUtils

class UserRepository {

    fun sendUser(
        email: String,
        password: String,
        callback: (Boolean) -> Unit
    ) {
        NetworkUtils.sendUser(email, password, callback)
    }
}