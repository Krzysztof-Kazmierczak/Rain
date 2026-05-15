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
    var lastStmTimeStr: String? = null
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
            if (!isMachineWorking || lastDataTimestamp == 0L) return

            val now = System.currentTimeMillis()

            // Używamy gotowego timestampa zamiast parsować Stringa co sekundę
            val diffSec = (now - lastDataTimestamp) / 1000

            if (diffSec >= 0) {
                // 1. CZAS PRACY
                val currentWorkTimeSec = baseWorkTimeSec + diffSec

                // 2. ROZWINIĘCIE
                val speedPerSecond = baseSpeed / 3600.0
                val distanceTraveled = speedPerSecond * diffSec
                val currentExtension = (baseExtension - distanceTraveled).coerceAtLeast(0.0)

                // 3. CZAS DO KOŃCA
                val timeToFinishSec = if (baseSpeed > 0) {
                    (currentExtension / speedPerSecond).toLong()
                } else 0L

                // Aktualizacja UI
                val label = if (currentIsOffline) "[Offline] " else ""
                workTimeText.text = "${label}Czas pracy: ${formatSecondsToTimeStr(currentWorkTimeSec)}"
                extensionText.text = String.format(Locale.getDefault(), "%sRozwinięcie: %.1f m", label, currentExtension)
                timeFinishText.text = "${label}Czas do końca: ${formatSecondsToTimeStr(timeToFinishSec)}"
            }

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

    private fun testPobieraniaIWyświetlaniaLogow() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        // Pobieramy dane z tabeli INT
        remoteRepo.getRainAdvInt(currentRainId, email) { advInt ->
            if (advInt != null) {
                Log.d("TEST_DANYCH", "=== DANE INT POBRANE ===")
                Log.d("TEST_DANYCH", advInt.toString()) // Wypisze wszystkie zmienne w konsoli
            } else {
                Log.e("TEST_DANYCH", "Brak danych INT")
            }
        }

        // Pobieramy dane z tabeli UINT
        remoteRepo.getRainAdvUInt(currentRainId, email) { advUInt ->
            if (advUInt != null) {
                Log.d("TEST_DANYCH", "=== DANE UINT POBRANE ===")
                Log.d("TEST_DANYCH", advUInt.toString()) // Wypisze wszystkie zmienne w konsoli
            } else {
                Log.e("TEST_DANYCH", "Brak danych UINT")
            }
        }
    }

    private fun wyslijWartosciDziewiec() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        // 1. Aktualizacja INT (dane_stm = 9)
        remoteRepo.getRainAdvInt(currentRainId, email) { stareDaneInt ->
            if (stareDaneInt != null) {
                // Kopiujemy cały stary obiekt, zmieniając TYLKO daneStm
                val noweDaneInt = stareDaneInt.copy(daneStm = 10)

                remoteRepo.saveRainAdvInt(email, noweDaneInt) { success ->
                    runOnUiThread {
                        if (success) Log.d("TEST_ZAPISU", "Zapisano INT z dane_stm=9")
                        else Log.e("TEST_ZAPISU", "Błąd zapisu INT")
                    }
                }
            }
        }

        // 2. Aktualizacja UINT (zrodlo_danych = 9)
        remoteRepo.getRainAdvUInt(currentRainId, email) { stareDaneUInt ->
            if (stareDaneUInt != null) {
                // Kopiujemy cały stary obiekt, zmieniając TYLKO zrodloDanych
                val noweDaneUInt = stareDaneUInt.copy(zrodloDanych = 9)

                remoteRepo.saveRainAdvUInt(email, noweDaneUInt) { success ->
                    runOnUiThread {
                        if (success) {
                            Log.d("TEST_ZAPISU", "Zapisano UINT z zrodlo_danych=9")
                            Toast.makeText(this@RainDetailsActivity, "Wysłano sygnał (9)", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("TEST_ZAPISU", "Błąd zapisu UINT")
                        }
                    }
                }
            }
        }
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
            //wyslijWartosciDziewiec()

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
                statusText.text = "${label}Status: GOTOWOŚĆ 🟡"
                statusText.setTextColor(Color.BLUE)
            }
            2 -> {
                statusText.text = "${label}Status: PRACUJE ✅"
                statusText.setTextColor(Color.GREEN)
            }
            5 -> {
                statusText.text = "${label}Status: Utracono łączność. Poprzedni stan: Praca ⚠️"
                statusText.setTextColor(Color.parseColor("#FFA500")) // Pomarańczowy
            }
            6 -> {
                statusText.text = "${label}Status: Utracono łączność. Poprzedni stan: Gotowość ⚠️"
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

                // KLUCZOWA ZMIANA: Przypisujemy czas z serwera/urządzenia do zmiennej używanej przez ticker
                lastStmTimeStr = latest.updatedAt

                // Logujemy dla pewności, co przychodzi z serwera
                Log.d("TICKER_DEBUG", "Pobrano nowy czas STM: $lastStmTimeStr")

                currentIsOffline = false
                isMachineWorking = (latest.isWorking == 2 || latest.isWorking == 6)

                baseWorkTimeSec = parseTimeStrToSeconds(latest.workTime)
                baseExtension = latest.extension.toString().toDoubleOrNull() ?: 0.0
                baseSpeed = latest.currentSpeed

                refreshStatusUI(
                    latest.isWorking, latest.currentSpeed, latest.timeToFinish,
                    latest.workTime, latest.extension.toString(), currentIsOffline, latest.signalStrength
                )

                CacheHelper.saveObject(this, "RAIN_LIVE_STATUS_$currentRainId", latest)

                tickerHandler.removeCallbacks(liveTickerRunnable)
                if (isMachineWorking) {
                    tickerHandler.post(liveTickerRunnable)
                }
            } else {
                // Logika dla cache...
                tickerHandler.removeCallbacks(liveTickerRunnable)
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

        remoteRepo.getStmUpdateInfo(currentRainId, email) { czasStm, opoznienieAktualizacji ->
            if (czasStm != null && opoznienieAktualizacji != null) {
                // Jeśli displayUpdateTimes oczekuje Int jako drugi parametr
                val delay = opoznienieAktualizacji.toIntOrNull() ?: 0

                runOnUiThread {
                    displayUpdateTimes(czasStm, delay)
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
       // testPobieraniaIWyświetlaniaLogow()
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

    private fun displayUpdateTimes(lastUpdateStr: String?, delayMinutes: Int) {
        if (lastUpdateStr.isNullOrEmpty() || lastUpdateStr == "null") return

        // Zabezpieczenie przed błędną wartością opóźnienia
        val safeDelayMinutes = if (delayMinutes > 0) delayMinutes else 1

        try {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

            val lastDate = dbFormat.parse(lastUpdateStr) ?: return

            // Zapisujemy timestamp dla tickera
            lastDataTimestamp = lastDate.time

            val currentTime = Calendar.getInstance().time


            tvLastUpdate.text = "Ostatnia aktualizacja z urządzenia: ${displayFormat.format(lastDate)}"

            // Wyliczamy przewidywaną następną aktualizację
            val calendar = Calendar.getInstance()
            calendar.time = lastDate
            calendar.add(Calendar.MINUTE, safeDelayMinutes)
            val nextDate = calendar.time

            // Ile minut minęło od planowanej aktualizacji
            val diffMillis = currentTime.time - nextDate.time
            val diffMinutes = diffMillis / (1000 * 60)

            when {
                // Brak łączności, jeśli minęło więcej niż 2x interwał aktualizacji
                diffMinutes > safeDelayMinutes * 2L -> {
                    tvNextUpdate.setTextColor(Color.RED)
                    tvNextUpdate.text =
                        "Przewidywana następna: ${displayFormat.format(nextDate)}\n" +
                                "Utracono łączność z urządzeniem."
                }

                // Termin aktualizacji już minął, ale jeszcze nie uznajemy utraty łączności
                diffMinutes > 0 -> {
                    tvNextUpdate.setTextColor(Color.DKGRAY)
                    tvNextUpdate.text =
                        "Przewidywana następna: ${displayFormat.format(nextDate)}"
                }

                // Aktualizacja jest jeszcze w przyszłości
                else -> {
                    tvNextUpdate.setTextColor(Color.GRAY)
                    tvNextUpdate.text =
                        "Przewidywana następna: ${displayFormat.format(nextDate)}"
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