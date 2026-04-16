package com.example.bazadanych.ui

import android.content.Intent
import android.graphics.Color
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
import com.example.bazadanych.data.db.RainStatus
import com.example.bazadanych.data.local_db.CacheHelper
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import org.osmdroid.config.Configuration

class RainDetailsActivity : AppCompatActivity() {

    private lateinit var currentRainId: String
    private val remoteRepo = RainRemoteRepository()
    private val refreshHandler = Handler(Looper.getMainLooper())

    private lateinit var nameEdit: EditText
    private lateinit var lengthEdit: EditText
    private lateinit var commentEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var currentSpeedText: TextView
    private lateinit var timeFinishText: TextView
    private lateinit var workTimeText: TextView
    private lateinit var extensionText: TextView
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadLiveStatus()
            refreshHandler.postDelayed(this, 30000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_rain_details)

        currentRainId = intent.getStringExtra("id") ?: ""

        initUI()
        loadInitialData()
    }

    private fun initUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        nameEdit = findViewById(R.id.nameEdit)
        lengthEdit = findViewById(R.id.lengthEdit)
        commentEdit = findViewById(R.id.commentEdit)
        statusText = findViewById(R.id.statusText)
        currentSpeedText = findViewById(R.id.currentSpeedText)
        timeFinishText = findViewById(R.id.timeFinishText)
        workTimeText = findViewById(R.id.workTimeText)
        extensionText = findViewById(R.id.extensionText)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveMainData()
        }

        findViewById<Button>(R.id.btnAdvancedSettings).setOnClickListener {
            val intent = Intent(this, AdvancedSettingsActivity::class.java)
            intent.putExtra("id", currentRainId)
            // Pobieramy aktualną wartość z pola lengthEdit i wysyłamy dalej
            intent.putExtra("max_hose_length", lengthEdit.text.toString())
            startActivity(intent)
        }
    }

    private fun refreshStatusUI(isWorking: Boolean, speed: Double, finishTime: String, workTime: String, extension: String, isOffline: Boolean) {
        val label = if (isOffline) "[Offline] " else ""

        statusText.text = "${label}Status: ${if (isWorking) "PRACUJE ✅" else "STOP 🛑"}"
        statusText.setTextColor(if (isWorking) Color.GREEN else Color.RED)

        currentSpeedText.text = "${label}Prędkość: $speed m/h"
        timeFinishText.text = "${label}Czas do końca: $finishTime"

        // NOWE POLA
        workTimeText.text = "${label}Czas pracy: $workTime"
        extensionText.text = "${label}Rozwinięcie: $extension m"
    }

    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return

        remoteRepo.getRainHistory(currentRainId) { history ->
            if (history.isNotEmpty()) {
                val latest = history[0]

                // TODO: Zaktualizuj 'workTime' i 'extension', gdy dodasz te pola do bazy i obiektu RainStatus
                refreshStatusUI(
                    isWorking = latest.isWorking,
                    speed = latest.currentSpeed,
                    finishTime = latest.timeToFinish,
                    workTime = "00:00:00", // Atrapa - podmień na dane z bazy
                    extension = "0", // Atrapa - podmień na dane z bazy
                    isOffline = false
                )
                CacheHelper.saveObject(this, "RAIN_LIVE_STATUS_$currentRainId", latest)
            } else {
                val cached = CacheHelper.loadObject<RainStatus>(this, "RAIN_LIVE_STATUS_$currentRainId")
                cached?.let {
                    refreshStatusUI(it.isWorking, it.currentSpeed, it.timeToFinish, "00:00:00", "0", true)
                }
            }
        }
    }

    private fun loadInitialData() {
        if (currentRainId.isEmpty()) return

        val detailCacheKey = "RAIN_DETAILS_$currentRainId"
        val cachedRain = CacheHelper.loadObject<Rain>(this, detailCacheKey)
        cachedRain?.let {
            nameEdit.setText(it.name)
            lengthEdit.setText(it.hoseLength)
            commentEdit.setText(it.comment)
            supportActionBar?.title = "[Offline] ${it.name}"
        }

        remoteRepo.getRainDetails(currentRainId) { name, length, comment ->
            if (name.isNotEmpty()) {
                nameEdit.setText(name)
                lengthEdit.setText(length)
                commentEdit.setText(comment)
                supportActionBar?.title = name

                val rainToCache = Rain(currentRainId, name, length, comment, false)
                CacheHelper.saveObject(this, detailCacheKey, rainToCache)
            }
        }
        loadLiveStatus()
    }

    private fun saveMainData() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        val rain = Rain(
            id = currentRainId,
            name = nameEdit.text.toString(),
            hoseLength = lengthEdit.text.toString(),
            comment = commentEdit.text.toString(),
            isWorking = false
        )

        remoteRepo.saveRain(email, rain) { success ->
            runOnUiThread {
                if (success) {
                    CacheHelper.saveObject(this@RainDetailsActivity, "RAIN_DETAILS_$currentRainId", rain)
                    Toast.makeText(this, "Zapisano dane ✅", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}