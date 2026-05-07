package com.example.bazadanych.data.db

data class WorkerResponse(
    val role: String,
    val my_level: String?, // Może być null, jeśli role == "none" lub "owner"
    val owner: String?,
    val workers: List<Worker>
)

data class Worker(
    val id: Int,
    val owner_email: String,
    val worker_email: String,
    val access_level: Int,
    val created_at: String
)