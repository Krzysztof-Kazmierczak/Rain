package com.example.bazadanych.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar

class CreateRainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_rain)

        // 1. Inicjalizacja Toolbara (Górnego paska)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 2. Mapowanie widoków z XML
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val hoseInput = findViewById<EditText>(R.id.hoseInput)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val rainSpinner = findViewById<Spinner>(R.id.rainSpinner)
        val addButton = findViewById<Button>(R.id.addButton)

        // 3. Konfiguracja Spinnera (Rozwijanej listy)
        val rainList = listOf(
            "Wybierz deszczownię",
            "RM180-R (300 m)",
            "RM200-S (450 m)"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            rainList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rainSpinner.adapter = adapter
        rainSpinner.setSelection(0)

        // Słuchacz wyboru ze Spinnera - automatyczne uzupełnianie pól
        rainSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // Wybierz deszczownię
                        nameInput.setText("")
                        hoseInput.setText("")
                    }
                    1 -> {
                        nameInput.setText("RM180-R")
                        hoseInput.setText("300")
                    }
                    2 -> {
                        nameInput.setText("RM200-S")
                        hoseInput.setText("450")
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Obsługa kliknięcia przycisku "DODAJ"
        addButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val length = hoseInput.text.toString().trim()
            val comment = commentInput.text.toString().trim()

            // Walidacja pól
            if (name.isEmpty() || length.isEmpty()) {
                Toast.makeText(this, "Wypełnij nazwę i długość węża", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Tworzenie obiektu Rain
            val rain = Rain(
                id = "",
                name = name,
                hoseLength = length,
                comment = comment,
                isWorking = false
            )

            // Pobieranie e-maila zalogowanego użytkownika z SharedPreferences
            val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
            val email = prefs.getString("user_email", "") ?: ""

            if (email.isEmpty()) {
                Toast.makeText(this, "Błąd sesji. Zaloguj się ponownie.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Wysłanie danych do bazy MySQL za pomocą nowego repozytorium
            val remoteRepo = RainRemoteRepository()
            remoteRepo.saveRain(email, rain) { success ->
                // Powrót do wątku głównego (UI), aby móc zarządzać widokiem
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Dodano pomyślnie na serwer! ✅", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish() // Zamknięcie aktywności
                    } else {
                        Toast.makeText(this, "Błąd serwera przy zapisie ❌", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}