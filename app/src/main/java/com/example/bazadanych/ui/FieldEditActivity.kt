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
import com.google.android.material.card.MaterialCardView

class FieldEditActivity : AppCompatActivity() {

    private lateinit var nameEdit: EditText
    private lateinit var cropEdit: EditText
    private lateinit var commentEdit: EditText
    private lateinit var areaText: TextView
    private lateinit var btnDeleteField: Button // Deklarujemy tutaj...

    private val remoteRepo = RainRemoteRepository()
    private var fieldId: String = "0"
    private var coordinates: String = ""
    private var areaHa: Double = 0.0
    private var currentColor: String = "#604CAF50"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_field_edit)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // KOLEJNOŚĆ JEST KLUCZOWA:
        initViews()      // 1. Najpierw znajdujemy przyciski
        loadIntentData() // 2. Potem wczytujemy dane i decydujemy czy pokazać usuwanie
    }

    private fun initViews() {
        nameEdit = findViewById(R.id.fieldNameEdit)
        cropEdit = findViewById(R.id.cropTypeEdit)
        commentEdit = findViewById(R.id.fieldCommentEdit)
        areaText = findViewById(R.id.fieldAreaText)
        btnDeleteField = findViewById(R.id.btnDeleteField) // ...a przypisujemy TUTAJ

        val cardGreen = findViewById<MaterialCardView>(R.id.colorGreen)
        val cardYellow = findViewById<MaterialCardView>(R.id.colorYellow)
        val cardBlue = findViewById<MaterialCardView>(R.id.colorBlue)

        btnDeleteField.setOnClickListener {
            val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
            val email = prefs.getString("user_email", "") ?: ""

            if (fieldId != "0" && email.isNotEmpty()) {
                remoteRepo.deleteAgriculturalField(email, fieldId) { success ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, "Pole zostało usunięte! 🗑️", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Błąd podczas usuwania pola.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

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

        findViewById<Button>(R.id.btnSaveField).setOnClickListener {
            saveField()
        }
    }

    private fun updateColorStroke(
        selected: MaterialCardView,
        other1: MaterialCardView,
        other2: MaterialCardView
    ) {
        selected.strokeWidth = 6
        selected.strokeColor = android.graphics.Color.parseColor("#424242")
        other1.strokeWidth = 0
        other2.strokeWidth = 0
    }

    private fun loadIntentData() {
        fieldId = intent.getStringExtra("field_id") ?: "0"
        coordinates = intent.getStringExtra("coords") ?: ""
        areaHa = intent.getDoubleExtra("area", 0.0)

        areaText.text = "Powierzchnia: ${String.format("%.2f", areaHa)} ha"

        if (fieldId != "0") {
            btnDeleteField.visibility = View.VISIBLE
            val name = intent.getStringExtra("name") ?: ""
            val crop = intent.getStringExtra("crop") ?: ""
            val comment = intent.getStringExtra("comment") ?: ""
            val color = intent.getStringExtra("color") ?: "#604CAF50"

            nameEdit.setText(name)
            cropEdit.setText(crop)
            commentEdit.setText(comment)
            currentColor = color

            val cardGreen = findViewById<MaterialCardView>(R.id.colorGreen)
            val cardYellow = findViewById<MaterialCardView>(R.id.colorYellow)
            val cardBlue = findViewById<MaterialCardView>(R.id.colorBlue)

            when {
                color.contains("4CAF50", ignoreCase = true) -> updateColorStroke(cardGreen, cardYellow, cardBlue)
                color.contains("FFEB3B", ignoreCase = true) -> updateColorStroke(cardYellow, cardGreen, cardBlue)
                color.contains("2196F3", ignoreCase = true) -> updateColorStroke(cardBlue, cardGreen, cardYellow)
            }
        } else {
            btnDeleteField.visibility = View.GONE
        }
    }

    private fun saveField() {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val email = prefs.getString("user_email", "") ?: ""

        if (email.isEmpty()) {
            Toast.makeText(this, "Błąd: Użytkownik niezalokowany!", Toast.LENGTH_SHORT).show()
            return
        }

        remoteRepo.saveAgriculturalField(
            email = email,
            id = fieldId,
            name = nameEdit.text.toString(),
            areaHa = areaHa,
            cropType = cropEdit.text.toString(),
            comment = commentEdit.text.toString(),
            color = currentColor,
            coords = coordinates
        ) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Pole zapisane! 🌾", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Serwer odrzucił zapis.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}