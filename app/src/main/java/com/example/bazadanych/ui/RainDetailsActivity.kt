package com.example.bazadanych.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar

class RainDetailsActivity : AppCompatActivity() {

    private lateinit var currentRainId: String
    private val remoteRepo = RainRemoteRepository()

    // Pola widoku
    private lateinit var nameEdit: EditText
    private lateinit var lengthEdit: EditText
    private lateinit var commentEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var currentSpeedText: TextView
    private lateinit var timeFinishText: TextView

    // ⏱️ MECHANIZM AUTO-ODŚWIEŻANIA
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadLiveStatus() // Pobierz dane
            refreshHandler.postDelayed(this, 30000) // Wywołaj ponowne odpalenie za 30 000 ms (30 sekund)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rain_details)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Mapowanie pól stałych
        nameEdit = findViewById(R.id.nameEdit)
        lengthEdit = findViewById(R.id.lengthEdit)
        commentEdit = findViewById(R.id.commentEdit)

        // Mapowanie pól zmiennych
        statusText = findViewById(R.id.statusText)
        currentSpeedText = findViewById(R.id.currentSpeedText)
        timeFinishText = findViewById(R.id.timeFinishText)

        val saveButton = findViewById<Button>(R.id.saveButton)
        val deleteButton = findViewById<Button>(R.id.deleteButton)

        currentRainId = intent.getStringExtra("id") ?: ""

        if (currentRainId.isNotEmpty()) {
            // 1. Pobierz dane stałe (tylko RAZ przy wejściu do ekranu, bo ich nie edytujesz co chwilę)
            remoteRepo.getRainDetails(currentRainId) { rain ->
                runOnUiThread {
                    rain?.let {
                        nameEdit.setText(it.name)
                        lengthEdit.setText(it.hoseLength)
                        commentEdit.setText(it.comment)
                    }
                }
            }
        }

        // --- Logika przycisków Zapisz/Usuń ---
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        saveButton.setOnClickListener {
            val rain = Rain(
                id = currentRainId,
                name = nameEdit.text.toString(),
                hoseLength = lengthEdit.text.toString(),
                comment = commentEdit.text.toString(),
                isWorking = false // 👈 Wpisujemy cokolwiek, bo skrypt PHP i tak tego nie aktualizuje
            )
            remoteRepo.saveRain(email, rain) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Zapisano ✅", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }

        deleteButton.setOnClickListener {
            remoteRepo.deleteRain(currentRainId) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Usunięto 🗑️", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    // 📥 Funkcja pobierająca najświeższy status z bazy
    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return

        remoteRepo.getRainHistory(currentRainId) { history ->
            runOnUiThread {
                if (history.isNotEmpty()) {
                    val latest = history[0]
                    statusText.text = if (latest.isWorking) "Status: PRACUJE ✅" else "Status: POSTÓJ 🛑"
                    currentSpeedText.text = "Aktualna prędkość: ${latest.currentSpeed} m/h"
                    timeFinishText.text = "Czas do końca: ${latest.timeToFinish}"

                    // Opcjonalnie: mały dymek informujący o odświeżeniu
                    Toast.makeText(this, "Zaktualizowano dane live 🔄", Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "Status: Brak danych"
                }
            }
        }
    }

    // Ekran staje się widoczny -> odpalamy pętlę odświeżania
    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    // Użytkownik minimalizuje apkę lub wychodzi z ekranu -> zatrzymujemy pętlę! (Bateria Ci podziękuje)
    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}