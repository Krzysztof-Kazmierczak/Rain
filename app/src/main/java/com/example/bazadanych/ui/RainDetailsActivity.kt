package com.example.bazadanych.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private lateinit var signalText: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvNextUpdate: TextView

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
        signalText = findViewById(R.id.signalText)

        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvNextUpdate = findViewById(R.id.tvNextUpdate)

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

    private fun refreshStatusUI(isWorking: Int, speed: Double, finishTime: String, workTime: String, extension: String, isOffline: Boolean, signal: Int) {
        val label = if (isOffline) "[Offline] " else ""

        // Zmiana: isWorking == 1
        statusText.text = "${label}Status: ${if (isWorking == 1) "PRACUJE ✅" else "STOP 🛑"}"

        // Zmiana: isWorking == 1
        statusText.setTextColor(if (isWorking == 1) Color.GREEN else Color.RED)

        currentSpeedText.text = "${label}Prędkość: $speed m/h"
        timeFinishText.text = "${label}Czas do końca: $finishTime"

        workTimeText.text = "${label}Czas pracy: $workTime"
        extensionText.text = "${label}Rozwinięcie: $extension m"

        signalText.text = "${label}Zasięg: $signal dBm"
    }

    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return

        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.getRainHistory(currentRainId, email) { history ->
            if (history.isNotEmpty()) {
                val latest = history[0]

                refreshStatusUI(
                    isWorking = latest.isWorking,
                    speed = latest.currentSpeed,
                    finishTime = latest.timeToFinish,
                    workTime = latest.workTime,
                    extension = latest.extension.toString(),
                    isOffline = false,
                    signal = latest.signalStrength // Przekazujemy sygnał z serwera
                )
                CacheHelper.saveObject(this, "RAIN_LIVE_STATUS_$currentRainId", latest)
            } else {
                // TUTAJ BYŁ BŁĄD - brakowało siódmego parametru
                val cached = CacheHelper.loadObject<RainStatus>(this, "RAIN_LIVE_STATUS_$currentRainId")
                cached?.let {
                    refreshStatusUI(
                        isWorking = it.isWorking,
                        speed = it.currentSpeed,
                        finishTime = it.timeToFinish,
                        workTime = it.workTime,       // Lepiej brać z cache niż wpisywać "00:00:00"
                        extension = it.extension.toString(),
                        isOffline = true,
                        signal = it.signalStrength    // DODANO BRAKUJĄCY PARAMETR
                    )
                }
            }
        }
    }

    private fun loadInitialData() {
        if (currentRainId.isEmpty()) return

        val detailCacheKey = "RAIN_DETAILS_$currentRainId"
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        Log.d("RainRepoDetails", "Pobrany email z sesji: '$email' dla ID: $currentRainId")
        val cachedRain = CacheHelper.loadObject<Rain>(this, detailCacheKey)
        cachedRain?.let {
            nameEdit.setText(it.name)
            lengthEdit.setText(it.hoseLength)
            commentEdit.setText(it.comment)
            supportActionBar?.title = "[Offline] ${it.name}"
        }

        remoteRepo.getStmUpdateInfo(currentRainId, email) { json ->
            if (json != null && !json.has("error")) {
                val lastUpdate = json.optString("last_update")
                val delay = json.optInt("update_delay")
                val dane = json.optInt("dane_stm")

                displayUpdateTimes(lastUpdate, delay, dane)
            }
        }

        remoteRepo.getRainDetails(currentRainId, email) { name, length, comment ->
            Log.d("MOJ_TEST", "Odebrano z repo: name='$name', length='$length'")
            if (name.isNotEmpty()) {
                nameEdit.setText(name)
                lengthEdit.setText(length)
                commentEdit.setText(comment)
                supportActionBar?.title = name

                // Zapisz do cache
                val rainToCache = Rain(currentRainId, name, length, comment, 0)
                CacheHelper.saveObject(this, "RAIN_DETAILS_$currentRainId", rainToCache)
            }
            else {
                Log.e("MOJ_TEST", "Nazwa jest pusta, dlatego napis [Offline] został!")
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
            isWorking = 0
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

    private fun displayUpdateTimes(lastUpdateStr: String?, delayMinutes: Int, dane: Int) {
        if (lastUpdateStr.isNullOrEmpty() || lastUpdateStr == "null") return

        // Unikalny klucz dla zapamiętania daty kontaktu dla konkretnej maszyny
        val cacheKey = "LAST_SUCCESSFUL_CONTACT_$currentRainId"

        try {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

            val lastDate = dbFormat.parse(lastUpdateStr) ?: return
            val currentTime = Calendar.getInstance().time

            // 1. Obsługa pola Ostatnia Aktualizacja + Cache
            when (dane) {
                6 -> {
                    // Jeśli utracono łączność, próbujemy wyciągnąć ostatnią dobrą datę z pamięci telefonu
                    val cachedDate = CacheHelper.loadObject<String>(this, cacheKey)
                    if (!cachedDate.isNullOrEmpty()) {
                        tvLastUpdate.text = "Ostatnia aktualizacja z urządzenia: $cachedDate"
                    } else {
                        tvLastUpdate.text = ""
                    }
                }
                else -> {
                    // Jeśli urządzenie jest online (!= 6), formatujemy datę, wyświetlamy i zapisujemy do cache
                    val formattedDate = displayFormat.format(lastDate)
                    tvLastUpdate.text = "Ostatnia aktualizacja z urządzenia: $formattedDate"
                    CacheHelper.saveObject(this, cacheKey, formattedDate)
                }
            }

            // 2. Planowana następna aktualizacja (Obliczenia czasu)
            val calendar = Calendar.getInstance()
            calendar.time = lastDate
            calendar.add(Calendar.MINUTE, delayMinutes)
            val nextDate = calendar.time

            val diffMillis: Long = currentTime.time - nextDate.time
            val diffMinutes = diffMillis / (1000 * 60)

            // 3. Obsługa pola Przewidywana Następna
            when {
                dane == 6 -> {
                    tvNextUpdate.setTextColor(Color.RED)
                    tvNextUpdate.text = "Brak danych z urządzenia - utracono łączność."
                }

                diffMinutes > delayMinutes * 2 -> {
                    tvNextUpdate.setTextColor(Color.RED)
                    tvNextUpdate.text = "Przewidywana następna: ${displayFormat.format(nextDate)}\n" +
                            "Utracono łączność z urządzeniem."
                }

                diffMinutes > 0 -> {
                    tvNextUpdate.setTextColor(Color.DKGRAY)
                    tvNextUpdate.text = "Przewidywana następna: ${displayFormat.format(nextDate)}"
                }

                else -> {
                    tvNextUpdate.setTextColor(Color.GRAY)
                    tvNextUpdate.text = "Przewidywana następna: ${displayFormat.format(nextDate)}"
                }
            }

        } catch (e: Exception) {
            Log.e("TIME_ERROR", "Błąd formatowania daty: ${e.message}")
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