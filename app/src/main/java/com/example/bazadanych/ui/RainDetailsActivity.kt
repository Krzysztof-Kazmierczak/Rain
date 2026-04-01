package com.example.bazadanych.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.repository.RainRemoteRepository
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

        // 📥 Pobieranie danych przekazanych z HomeActivity
        currentRainId = intent.getStringExtra("id") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val length = intent.getStringExtra("length") ?: ""
        val comment = intent.getStringExtra("comment") ?: ""

        // 📌 Ustawienie danych w polach edycyjnych
        nameEdit.setText(name)
        lengthEdit.setText(length)
        commentEdit.setText(comment)

        val remoteRepo = RainRemoteRepository()

        // Pobieramy email z SharedPreferences
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: ""

        // 💾 ZAPIS / EDYCJA
        saveButton.setOnClickListener {
            val updatedName = nameEdit.text.toString().trim()
            val updatedLength = lengthEdit.text.toString().trim()
            val updatedComment = commentEdit.text.toString().trim()

            if (updatedName.isEmpty() || updatedLength.isEmpty()) {
                Toast.makeText(this, "Nazwa i długość nie mogą być puste", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Tworzymy obiekt z TYM SAMYM ID, żeby baza wiedziała, że robimy UPDATE
            val updatedRain = Rain(
                id = currentRainId,
                name = updatedName,
                hoseLength = updatedLength,
                comment = updatedComment
            )

            remoteRepo.saveRain(email, updatedRain) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Zmiany zapisane ✅", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this, "Błąd zapisu na serwerze ❌", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 🗑 USUWANIE
        deleteButton.setOnClickListener {
            if (currentRainId.isEmpty()) return@setOnClickListener

            remoteRepo.deleteRain(currentRainId) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Deszczownia usunięta 🗑️", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this, "Błąd podczas usuwania ❌", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}