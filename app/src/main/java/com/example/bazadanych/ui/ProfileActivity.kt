package com.example.bazadanych.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar

class ProfileActivity : AppCompatActivity() {

    private val remoteRepo = RainRemoteRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // =================== NOWE LINIE DO STRZAŁKI POWROTU ===================
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Włączamy fizyczną strzałkę na pasku
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Kliknięcie w strzałkę cofa nas do poprzedniego ekranu
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        // ======================================================================

        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvStats = findViewById<TextView>(R.id.tvProfileStats)

        // 1. Pobieramy zapisany e-mail z SharedPreferences
        val sharedPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val email = sharedPrefs.getString("user_email", "Brak maila") ?: "Brak maila"

        tvEmail.text = email

        // 2. Pobieramy deszczownie z bazy, żeby wyliczyć statystyki
        remoteRepo.getRains(email) { rains ->
            runOnUiThread {
                val totalRains = rains.size
                val activeRains = rains.count { it.isWorking } // Liczymy tylko te z true

                // Ustawiamy tekst w formacie: Wszystkie / Aktywne
                tvStats.text = "$activeRains / $totalRains"
            }
        }
    }
}