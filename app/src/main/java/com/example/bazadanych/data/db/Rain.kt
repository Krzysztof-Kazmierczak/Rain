package com.example.bazadanych.data.db

import java.util.UUID

data class Rain(
    val id: String = UUID.randomUUID().toString(), // unikalne ID
    val name: String,
    val hoseLength: String,
    val comment: String,
    val isWorking: Boolean
)