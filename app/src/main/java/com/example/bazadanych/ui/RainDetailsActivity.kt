package com.example.bazadanych.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.google.android.material.appbar.MaterialToolbar

class RainDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rain_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}