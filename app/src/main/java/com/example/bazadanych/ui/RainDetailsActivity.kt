package com.example.bazadanych.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import android.widget.EditText
import android.widget.Button
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.db.RainStorage
import com.google.android.material.appbar.MaterialToolbar

class RainDetailsActivity : AppCompatActivity() {

    private lateinit var currentRainId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rain_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        val nameEdit = findViewById<EditText>(R.id.nameEdit)
        val lengthEdit = findViewById<EditText>(R.id.lengthEdit)
        val commentEdit = findViewById<EditText>(R.id.commentEdit)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val deleteButton = findViewById<Button>(R.id.deleteButton)

        // 📥 pobieranie danych
        currentRainId = intent.getStringExtra("id") ?: java.util.UUID.randomUUID().toString()
        val name = intent.getStringExtra("name") ?: ""
        val length = intent.getStringExtra("length") ?: ""
        val comment = intent.getStringExtra("comment") ?: ""

        // 📌 ustawienie w polach
        nameEdit.setText(name)
        lengthEdit.setText(length)
        commentEdit.setText(comment)

        // 💾 ZAPIS
        saveButton.setOnClickListener {
            val updatedRain = Rain(
                id = currentRainId,
                name = nameEdit.text.toString(),
                hoseLength = lengthEdit.text.toString(),
                comment = commentEdit.text.toString()
            )
            RainStorage.saveRain(this, updatedRain)
            finish()
        }

        // 🗑 USUWANIE
        deleteButton.setOnClickListener {
            RainStorage.deleteRain(this, currentRainId)
            finish()
        }
    }
}