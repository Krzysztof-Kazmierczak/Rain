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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvStats = findViewById<TextView>(R.id.tvProfileStats)

        val sharedPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val email = sharedPrefs.getString("user_email", "Brak maila") ?: "Brak maila"
        tvEmail.text = email

        // 1. ZAWSZE Wczytuj dane offline (zapisane z HomeActivity)
        val cachedTiles = com.example.bazadanych.data.local_db.CacheHelper.loadList<com.example.bazadanych.ui.RainTile>(this, "HOME_TILES_CACHE")

        if (cachedTiles != null && cachedTiles.isNotEmpty()) {
            // Odfiltrujemy przycisk "Dodaj", liczymy tylko maszyny
            val actualMachines = cachedTiles.filter { !it.isAddButton }
            val activeRains = actualMachines.count { it.isWorking }
            tvStats.text = "$activeRains / ${actualMachines.size}"
        } else {
            tvStats.text = "0 / 0"
        }

        // 2. Próba aktualizacji w tle
        remoteRepo.getRains(email) { rains ->
            runOnUiThread {
                if (rains.isNotEmpty()) { // Aktualizuj TYLKO jeśli serwer coś zwrócił
                    val totalRains = rains.size
                    val activeRains = rains.count { it.isWorking }
                    tvStats.text = "$activeRains / $totalRains"
                }
            }
        }
    }
}