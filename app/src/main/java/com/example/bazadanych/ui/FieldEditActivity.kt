package com.example.bazadanych.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar

class FieldEditActivity : AppCompatActivity() {

    private lateinit var nameEdit: EditText
    private lateinit var cropEdit: EditText
    private lateinit var commentEdit: EditText
    private lateinit var areaText: TextView

    // Widoki nakładek zaznaczenia
    private lateinit var checkGreen: View
    private lateinit var checkYellow: View
    private lateinit var checkBlue: View

    private val remoteRepo = RainRemoteRepository()
    private var fieldId: String = "0" // "0" oznacza nowe pole
    private var coordinates: String = ""
    private var areaHa: Double = 0.0

    // Domyślny kolor dla bazy (z przeźroczystością #60)
    private var currentColor: String = "#604CAF50"

    // W onCreate zamień kolejność - najpierw widoki, potem dane
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_field_edit)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        initViews()      // 1. Najpierw przygotuj widoki
        loadIntentData() // 2. Potem wczytaj dane (nadpiszą domyślny zielony)
    }

    private fun initViews() {
        nameEdit = findViewById(R.id.fieldNameEdit)
        cropEdit = findViewById(R.id.cropTypeEdit)
        commentEdit = findViewById(R.id.fieldCommentEdit)
        areaText = findViewById(R.id.fieldAreaText)

        val cardGreen = findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorGreen)
        val cardYellow = findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorYellow)
        val cardBlue = findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorBlue)

        cardGreen.setOnClickListener {
            currentColor = "#604CAF50"
            updateColorStroke(cardGreen, cardYellow, cardBlue)
        }
        cardYellow.setOnClickListener {
            currentColor = "#60FFEB3B"
            updateColorStroke(cardYellow, cardGreen, cardBlue)
        }
        cardBlue.setOnClickListener {
            currentColor = "#602196F3"
            updateColorStroke(cardBlue, cardGreen, cardYellow)
        }

        // Domyślnie zaznacz zielony
        updateColorStroke(cardGreen, cardYellow, cardBlue)

        findViewById<Button>(R.id.btnSaveField).setOnClickListener {
            saveField()
        }
    }

    // Ta funkcja robi obwódkę wokół zaznaczonego kółka
    private fun updateColorStroke(
        selected: com.google.android.material.card.MaterialCardView,
        other1: com.google.android.material.card.MaterialCardView,
        other2: com.google.android.material.card.MaterialCardView
    ) {
        selected.strokeWidth = 6 // Grubość ramki dla wybranego
        selected.strokeColor = android.graphics.Color.parseColor("#424242") // Ciemnoszara ramka

        other1.strokeWidth = 0
        other2.strokeWidth = 0
    }

    private fun updateColorChecks(selected: String) {
        checkGreen.visibility = if (selected == "#4CAF50") View.VISIBLE else View.GONE
        checkYellow.visibility = if (selected == "#FFEB3B") View.VISIBLE else View.GONE
        checkBlue.visibility = if (selected == "#2196F3") View.VISIBLE else View.GONE
    }

    private fun loadIntentData() {
        fieldId = intent.getStringExtra("field_id") ?: "0"
        coordinates = intent.getStringExtra("coords") ?: ""
        areaHa = intent.getDoubleExtra("area", 0.0)

        areaText.text = "Powierzchnia: ${String.format("%.2f", areaHa)} ha"

        if (fieldId != "0") {
            // Odbieramy dane przesłane z mapy
            val name = intent.getStringExtra("name") ?: ""
            val crop = intent.getStringExtra("crop") ?: ""
            val comment = intent.getStringExtra("comment") ?: ""
            val color = intent.getStringExtra("color") ?: "#604CAF50"

            nameEdit.setText(name)
            cropEdit.setText(crop)
            commentEdit.setText(comment)
            currentColor = color

            // Pobieramy widoki kółek, żeby zaktualizować ramki
            val cardGreen = findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorGreen)
            val cardYellow = findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorYellow)
            val cardBlue = findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorBlue)

            // AKTUALIZACJA RAMKI NA START
            // Używamy ignoreCase, bo kolory w bazie mogą być małymi/dużymi literami
            when {
                color.contains("4CAF50", ignoreCase = true) -> updateColorStroke(cardGreen, cardYellow, cardBlue)
                color.contains("FFEB3B", ignoreCase = true) -> updateColorStroke(cardYellow, cardGreen, cardBlue)
                color.contains("2196F3", ignoreCase = true) -> updateColorStroke(cardBlue, cardGreen, cardYellow)
            }
        }
    }

    private fun saveField() {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: ""

        if (email.isEmpty()) {
            Toast.makeText(this, "Błąd: Użytkownik niezalokowany!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = nameEdit.text.toString()
        val crop = cropEdit.text.toString()
        val comment = commentEdit.text.toString()

        remoteRepo.saveAgriculturalField(
            email = email,
            id = fieldId,
            name = name,
            areaHa = areaHa,
            cropType = crop,
            comment = comment,
            color = currentColor,
            coords = coordinates
        ) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Pole zapisane! 🌾", Toast.LENGTH_SHORT).show()
                    finish() // Wraca do mapy
                } else {
                    Toast.makeText(this, "Serwer odrzucił zapis. Sprawdź Logcat!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}