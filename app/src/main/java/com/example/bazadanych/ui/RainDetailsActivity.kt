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

    // --- ZMIENNE DO ODLICZANIA NA ŻYWO (TICKER) ---
    private val tickerHandler = Handler(Looper.getMainLooper())
    private var baseWorkTimeSec = 0L
    private var baseExtension = 0.0
    private var baseSpeed = 0.0
    private var isMachineWorking = false
    private var lastDataTimestamp = 0L
    private var currentIsOffline = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadLiveStatus()
            refreshHandler.postDelayed(this, 30000)
        }
    }

    // --- LOGIKA TICKERA (Aktualizacja co 1 sekundę) ---
    private val liveTickerRunnable = object : Runnable {
        override fun run() {
            if (!isMachineWorking) return

            val now = System.currentTimeMillis()

            // Obliczamy ile sekund upłynęło od momentu wygenerowania danych
            val diffSec = if (lastDataTimestamp > 0) {
                ((now - lastDataTimestamp) / 1000).coerceAtLeast(0)
            } else {
                0L
            }

            // 1. CZAS PRACY (dodajemy upłynięte sekundy)
            val currentWorkTimeSec = baseWorkTimeSec + diffSec
            val formattedWorkTime = formatSecondsToTimeStr(currentWorkTimeSec)

            // 2. ROZWINIĘCIE (odejmujemy przebyty dystans)
            // Prędkość z bazy jest w m/h, zamieniamy na m/s
            val speedPerSecond = baseSpeed / 3600.0
            var currentExtension = baseExtension - (speedPerSecond * diffSec)
            if (currentExtension < 0) currentExtension = 0.0 // Zapobiegamy wartościom ujemnym

            // 3. CZAS DO KOŃCA (wyliczamy na nowo na podstawie pozostałego rozwinięcia)
            val timeToFinishSec = if (baseSpeed > 0) {
                (currentExtension / speedPerSecond).toLong()
            } else 0L
            val formattedFinishTime = formatSecondsToTimeStr(timeToFinishSec)

            // Aktualizujemy ekran
            val label = if (currentIsOffline) "[Offline] " else ""
            workTimeText.text = "${label}Czas pracy: $formattedWorkTime"
            extensionText.text = String.format(Locale.getDefault(), "%sRozwinięcie: %.1f m", label, currentExtension)
            timeFinishText.text = "${label}Czas do końca: $formattedFinishTime"

            // Zapętlamy - uruchom ponownie za 1 sekundę
            tickerHandler.postDelayed(this, 1000)
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
            intent.putExtra("max_hose_length", lengthEdit.text.toString())
            startActivity(intent)
        }
    }

    private fun refreshStatusUI(isWorking: Int, speed: Double, finishTime: String, workTime: String, extension: String, isOffline: Boolean, signal: Int) {
        val label = if (isOffline) "[Offline] " else ""

        // LOGIKA STATUSÓW
        when (isWorking) {
            0 -> {
                statusText.text = "${label}Status: WYŁĄCZONE ⚪"
                statusText.setTextColor(Color.GRAY)
            }
            1 -> {
                statusText.text = "${label}Status: GOTOWOŚĆ (Aktywna) 🟡"
                statusText.setTextColor(Color.BLUE)
            }
            2 -> {
                statusText.text = "${label}Status: PRACUJE ✅"
                statusText.setTextColor(Color.GREEN)
            }
            5 -> {
                statusText.text = "${label}Status: BRAK KONTAKTU (Oczekiwanie) ⚠️"
                statusText.setTextColor(Color.parseColor("#FFA500")) // Pomarańczowy
            }
            6 -> {
                statusText.text = "${label}Status: BRAK KONTAKTU (W pracy?) ⚠️"
                statusText.setTextColor(Color.RED)
            }
        }

        currentSpeedText.text = "${label}Prędkość: $speed m/h"
        signalText.text = "${label}Zasięg: $signal dBm"

        // Jeśli maszyna nie jest w trybie pracy (2 lub 6), wyświetlamy dane statyczne
        if (isWorking != 2 && isWorking != 6) {
            workTimeText.text = "${label}Czas pracy: $workTime"
            extensionText.text = "${label}Rozwinięcie: $extension m"
            timeFinishText.text = "${label}Czas do końca: $finishTime"
        }
    }

    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.getRainHistory(currentRainId, email) { history ->
            if (history.isNotEmpty()) {
                val latest = history[0]

                currentIsOffline = false
                // LICZNIK DZIAŁA DLA STATUSU 2 (Praca) i 6 (Błąd podczas pracy)
                isMachineWorking = (latest.isWorking == 2 || latest.isWorking == 6)

                baseWorkTimeSec = parseTimeStrToSeconds(latest.workTime)
                baseExtension = latest.extension.toString().toDoubleOrNull() ?: 0.0
                baseSpeed = latest.currentSpeed

                refreshStatusUI(
                    isWorking = latest.isWorking,
                    speed = latest.currentSpeed,
                    finishTime = latest.timeToFinish,
                    workTime = latest.workTime,
                    extension = latest.extension.toString(),
                    isOffline = currentIsOffline,
                    signal = latest.signalStrength
                )
                CacheHelper.saveObject(this, "RAIN_LIVE_STATUS_$currentRainId", latest)

                tickerHandler.removeCallbacks(liveTickerRunnable)
                if (isMachineWorking) {
                    tickerHandler.post(liveTickerRunnable)
                }
            } else {
                // Logika cache (bez zmian)
                val cached = CacheHelper.loadObject<RainStatus>(this, "RAIN_LIVE_STATUS_$currentRainId")
                cached?.let {
                    currentIsOffline = true
                    isMachineWorking = false
                    refreshStatusUI(it.isWorking, it.currentSpeed, it.timeToFinish, it.workTime, it.extension.toString(), true, it.signalStrength)
                }
            }
        }
    }

    private fun loadInitialData() {
        if (currentRainId.isEmpty()) return

        val detailCacheKey = "RAIN_DETAILS_$currentRainId"
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

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

                runOnUiThread {
                    displayUpdateTimes(lastUpdate, delay, dane)
                }
            }
        }

        remoteRepo.getRainDetails(currentRainId, email) { name, length, comment ->
            if (name.isNotEmpty()) {
                nameEdit.setText(name)
                lengthEdit.setText(length)
                commentEdit.setText(comment)
                supportActionBar?.title = name

                val rainToCache = Rain(currentRainId, name, length, comment, 0)
                CacheHelper.saveObject(this, "RAIN_DETAILS_$currentRainId", rainToCache)
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

        val cacheKey = "LAST_SUCCESSFUL_CONTACT_$currentRainId"

        try {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

            val lastDate = dbFormat.parse(lastUpdateStr) ?: return

            // --- ZAPISUJEMY TIMESTAMP DLA TICKERA ---
            lastDataTimestamp = lastDate.time

            val currentTime = Calendar.getInstance().time

            when (dane) {
                6 -> {
                    val cachedDate = CacheHelper.loadObject<String>(this, cacheKey)
                    if (!cachedDate.isNullOrEmpty()) {
                        tvLastUpdate.text = "Ostatnia aktualizacja z urządzenia: $cachedDate"
                    } else {
                        tvLastUpdate.text = ""
                    }
                }
                else -> {
                    val formattedDate = displayFormat.format(lastDate)
                    tvLastUpdate.text = "Ostatnia aktualizacja z urządzenia: $formattedDate"
                    CacheHelper.saveObject(this, cacheKey, formattedDate)
                }
            }

            val calendar = Calendar.getInstance()
            calendar.time = lastDate
            calendar.add(Calendar.MINUTE, delayMinutes)
            val nextDate = calendar.time

            val diffMillis: Long = currentTime.time - nextDate.time
            val diffMinutes = diffMillis / (1000 * 60)

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

    // --- FUNKCJE POMOCNICZE DLA TICKERA ---
    private fun parseTimeStrToSeconds(timeStr: String?): Long {
        if (timeStr.isNullOrEmpty()) return 0L
        val parts = timeStr.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                1 -> parts[0].toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatSecondsToTimeStr(totalSecs: Long): String {
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
        if (isMachineWorking) {
            tickerHandler.post(liveTickerRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
        tickerHandler.removeCallbacks(liveTickerRunnable) // Usypiamy ticker w tle oszczędzając baterię
    }
}