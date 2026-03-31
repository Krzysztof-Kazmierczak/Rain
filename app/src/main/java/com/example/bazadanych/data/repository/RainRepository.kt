package com.example.bazadanych.data.repository

import com.example.bazadanych.ui.RainTile

object RainRepository {

    val rains = mutableListOf(
        RainTile(
            title = "RM180-R",
            hoseLength = "300",
            comment = "Brak komentarza",
            isAddButton = false
        ),
        RainTile(
            title = "RM200-S",
            hoseLength = "450",
            comment = "Brak komentarza",
            isAddButton = false
        )
    )
}