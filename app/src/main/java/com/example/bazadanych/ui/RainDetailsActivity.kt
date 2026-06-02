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
    private lateinit var delayEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var currentSpeedText: TextView
    private lateinit var timeFinishText: TextView
    private lateinit var workTimeText: TextView
    private lateinit var extensionText: TextView
    private lateinit var signalText: TextView
    private lateinit var batteryText: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvNextUpdate: TextView

    // --- ZMIENNE DO ODLICZANIA NA ŻYWO (TICKER) ---
    private val tickerHandler = Handler(Looper.getMainLooper())
    private var baseWorkTimeSec = 0L
    private var baseExtension = 0.0
    private var baseSpeed = 0.0
    private var setSpeed = 0.0
    private var isMachineWorking = false
    private var lastDataTimestamp = 0L
    var lastStmTimeStr: String? = null
    private var currentIsOffline = false

    // --- POLA DLA PODLEWANIA STREFOWEGO ---
    private var isZonedWatering = false

    private var opoznionyStartPracy = 0
    private var opoznioneRozpoczecieZwijania = 0
    private var opoznioneZakonczeniePodlewania = 0
    private var zone1Speed = 0.0
    private var zone2Speed = 0.0
    private var zone3Speed = 0.0
    private var zone1Start = 0.0
    private var zone2Start = 0.0
    private var zone3Start = 0.0

    private var czasOpoznionyStartPracy = 0
    private var czasOpoznioneZwijanie = 0
    private var czasOpoznioneZakonczeniePodlewania = 0

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
            val diffSec = (now - lastDataTimestamp) / 1000

            if (diffSec >= 0) {
                var currentExtension = baseExtension

                // Łączny czas, przez który maszyna stoi na starcie i nie zwija węża
                val totalStartDelay = (if (opoznionyStartPracy == 1) czasOpoznionyStartPracy.toLong() else 0L) +
                        (if (opoznioneRozpoczecieZwijania == 1) czasOpoznioneZwijanie.toLong() else 0L)

                // 1. SYMULACJA ZWIJANIA
                for (i in 1..diffSec) {
                    val currentWorkTimeInLoop = baseWorkTimeSec + i

                    // Ruszamy ze zwijaniem dopiero gdy miną OBA opóźnienia startowe!
                    if (currentWorkTimeInLoop > totalStartDelay) {
                        val activeSpeed = getSpeedForExtension(currentExtension)
                        if (activeSpeed > 0.0) {
                            val speedPerSecond = activeSpeed / 3600.0
                            currentExtension -= speedPerSecond
                            if (currentExtension <= 0.0) currentExtension = 0.0
                        }
                    }
                }

                // 2. OBLICZENIE CZASÓW I AKTUALIZACJA UI
                val currentWorkTimeSec = baseWorkTimeSec + diffSec
                val timeToFinishSec = calculateTimeToFinishAnallytically(currentExtension, currentWorkTimeSec)

                // Sekcja aktualizacji widoków tekstowych (workTimeText, extensionText, itp.)
                val prefix = if (currentIsOffline) getString(R.string.offline_label) + " " else ""
                workTimeText.text = prefix + getString(R.string.work_time, formatSecondsToTimeStr(currentWorkTimeSec))
                extensionText.text = prefix + getString(R.string.extension, currentExtension)
                timeFinishText.text = prefix + getString(R.string.time_to_finish, formatSecondsToTimeStr(timeToFinishSec))

                // Odwrócone warunki sprawdzania strefy dla tickera
                if (isZonedWatering) {
                    val (currentZone, zoneSpeed) = when {
                        currentExtension <= zone1Start -> Pair(getString(R.string.zone_1), zone1Speed)
                        currentExtension <= zone2Start -> Pair(getString(R.string.zone_2), zone2Speed)
                        currentExtension <= zone3Start -> Pair(getString(R.string.zone_3), zone3Speed)
                        else -> Pair(getString(R.string.zone_base), setSpeed)
                    }
                    currentSpeedText.text = prefix + getString(R.string.speed_zone, zoneSpeed, currentZone)
                } else {
                    currentSpeedText.text = prefix + getString(R.string.speed, baseSpeed)
                }

                if (timeToFinishSec <= 0L) {
                    return
                }
            }

            tickerHandler.postDelayed(this, 1000)
        }
    }

    // Zwraca prędkość dla nowej logiki stref (0 -> strefa1 -> strefa2 -> strefa3 -> baza)
    private fun getSpeedForExtension(ext: Double): Double {
        if (!isZonedWatering) return setSpeed

        return when {
            ext <= zone1Start -> zone1Speed
            ext <= zone2Start -> zone2Speed
            ext <= zone3Start -> zone3Speed
            else -> setSpeed
        }
    }

    // Precyzyjne obliczanie czasu do końca dla nowej kolejności stref
    private fun calculateTimeToFinishAnallytically(ext: Double, czasPracySec: Long): Long {
        var totalRemainingTimeSec = 0.0

        // 1. OBLICZANIE CZASU JAZDY (Logika strefowa)
        if (ext > 0.0) {
            if (!isZonedWatering) {
                if (setSpeed > 0) {
                    totalRemainingTimeSec += ext / (setSpeed / 3600.0)
                }
            } else {
                var remainingDist = ext

                if (remainingDist > zone3Start) {
                    val chunk = remainingDist - zone3Start
                    if (setSpeed > 0) totalRemainingTimeSec += chunk / (setSpeed / 3600.0)
                    remainingDist = zone3Start
                }
                if (remainingDist > zone2Start && remainingDist <= zone3Start) {
                    val chunk = remainingDist - zone2Start
                    if (zone3Speed > 0) totalRemainingTimeSec += chunk / (zone3Speed / 3600.0)
                    remainingDist = zone2Start
                }
                if (remainingDist > zone1Start && remainingDist <= zone2Start) {
                    val chunk = remainingDist - zone1Start
                    if (zone2Speed > 0) totalRemainingTimeSec += chunk / (zone2Speed / 3600.0)
                    remainingDist = zone1Start
                }
                if (remainingDist > 0.0 && remainingDist <= zone1Start) {
                    val chunk = remainingDist
                    if (zone1Speed > 0) totalRemainingTimeSec += chunk / (zone1Speed / 3600.0)
                }
            }
        }

        // 2. ŁĄCZNE OPÓŹNIENIA STARTOWE (Praca + Zwijanie)
        // Sumujemy oba opóźnienia, które muszą minąć na początku
        val totalStartDelay = (if (opoznionyStartPracy == 1) czasOpoznionyStartPracy.toLong() else 0L) +
                (if (opoznioneRozpoczecieZwijania == 1) czasOpoznioneZwijanie.toLong() else 0L)

        // Od łącznego czasu startowego odejmujemy to, co maszyna już przepracowała
        val remainingStartDelay = maxOf(0L, totalStartDelay - czasPracySec)
        totalRemainingTimeSec += remainingStartDelay.toDouble()

        // 3. OPÓŹNIENIE KOŃCOWE (Tylko podlewanie na postoju po zwinięciu)
        // To opóźnienie czeka na maszynę na samym końcu (gdy ext dojdzie do 0.0)
        if (ext > 0.0) {
            if (opoznioneZakonczeniePodlewania == 1) {
                totalRemainingTimeSec += czasOpoznioneZakonczeniePodlewania.toDouble()
            }
        }

        return totalRemainingTimeSec.toLong()
    }

    private fun pobierzUstawieniaStrefowe(onComplete: () -> Unit) {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.getRainAdvInt(currentRainId, email) { advInt ->
            if (advInt != null) {
                isZonedWatering = advInt.podlewanieStrefowe
                if (isZonedWatering) {
                    zone1Speed = advInt.predkoscStrefa1.toDouble()
                    zone2Speed = advInt.predkoscStrefa2.toDouble()
                    zone3Speed = advInt.predkoscStrefa3.toDouble()
                }
                opoznionyStartPracy = if (advInt.opoznionyStartPracy) 1 else 0
                opoznioneRozpoczecieZwijania = if (advInt.opoznionyStartZwijania) 1 else 0
                opoznioneZakonczeniePodlewania = if (advInt.opoznioneZakonczenie) 1 else 0
            } else {
                Log.e("TEST_DANYCH", "Brak danych INT")
            }

            remoteRepo.getRainAdvUInt(currentRainId, email) { advUInt ->
                if (advUInt != null) {
                    zone1Start = advUInt.strefa1Start?.toDouble() ?: 0.0
                    zone2Start = advUInt.strefa2Start?.toDouble() ?: 0.0
                    zone3Start = advUInt.strefa3Start?.toDouble() ?: 0.0

                    czasOpoznionyStartPracy = (advUInt.opoznionyStartPracyH) * 3600 + (advUInt.opoznionyStartPracyMin) * 60 + (advUInt.opoznionyStartPracyS)
                    czasOpoznioneZwijanie = (advUInt.opoznionyStartZwijaniaH) * 3600 + (advUInt.opoznionyStartZwijaniaMin) * 60 + (advUInt.opoznionyStartZwijaniaS)
                    czasOpoznioneZakonczeniePodlewania = (advUInt.opoznioneZakonczeniePracyH) * 3600 + (advUInt.opoznioneZakonczeniePracyMin) * 60 + (advUInt.opoznioneZakonczeniePracyS)
                } else {
                    Log.e("TEST_DANYCH", "Brak danych UINT")
                }

                Log.d("TEST_DANYCH", "--- DANE STREFOWE POBRANE ---")
                Log.d("TEST_DANYCH", "Czy strefowe: $isZonedWatering")
                Log.d("TEST_DANYCH", "Prędkości: $zone1Speed | $zone2Speed | $zone3Speed")
                Log.d("TEST_DANYCH", "Miejsca startu: $zone1Start | $zone2Start | $zone3Start")

                onComplete()
            }
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
        delayEdit = findViewById(R.id.delayEdit)
        statusText = findViewById(R.id.statusText)
        currentSpeedText = findViewById(R.id.currentSpeedText)
        timeFinishText = findViewById(R.id.timeFinishText)
        workTimeText = findViewById(R.id.workTimeText)
        extensionText = findViewById(R.id.extensionText)
        signalText = findViewById(R.id.signalText)
        batteryText = findViewById(R.id.batteryText)

        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvNextUpdate = findViewById(R.id.tvNextUpdate)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveMainData()
            saveDelayData()
        }

        findViewById<Button>(R.id.btnAdvancedSettings).setOnClickListener {
            val intent = Intent(this, AdvancedSettingsActivity::class.java)
            intent.putExtra("id", currentRainId)
            intent.putExtra("max_hose_length", lengthEdit.text.toString())
            startActivity(intent)
        }
    }

    // Odwrócone warunki sprawdzania strefy dla głównej metody UI
    private fun refreshStatusUI(isWorking: Int, speed: Double, finishTime: String, workTime: String, extension: String, isOffline: Boolean, signal: Int, battery: Int) {
        val prefix = if (isOffline) getString(R.string.offline_label) + " " else ""

        when (isWorking) {
            0 -> {
                statusText.text = prefix + getString(R.string.status_off)
                statusText.setTextColor(Color.GRAY)
            }
            1 -> {
                statusText.text = prefix + getString(R.string.status_ready)
                statusText.setTextColor(Color.BLUE)
            }
            2 -> {
                statusText.text = prefix + getString(R.string.status_working)
                statusText.setTextColor(Color.GREEN)
            }
            5 -> {
                statusText.text = prefix + getString(R.string.status_connection_lost_working)
                statusText.setTextColor(Color.parseColor("#FFA500"))
            }
            6 -> {
                statusText.text = prefix + getString(R.string.status_connection_lost_ready)
                statusText.setTextColor(Color.RED)
            }
            9 -> {
                statusText.text = prefix + getString(R.string.status_connection_lost_3h)
                statusText.setTextColor(Color.RED)
            }
            10 -> {
                statusText.text = prefix + getString(R.string.status_should_finish)
                statusText.setTextColor(Color.RED)
            }
        }

        // --- LOGIKA WYŚWIETLANIA STREFY I PRĘDKOŚCI ---
        val extDouble = extension.toDoubleOrNull() ?: 0.0
        if (isZonedWatering) {
            val (currentZone, zoneSpeed) = when {
                extDouble <= zone1Start -> Pair(getString(R.string.zone_1), zone1Speed)
                extDouble <= zone2Start -> Pair(getString(R.string.zone_2), zone2Speed)
                extDouble <= zone3Start -> Pair(getString(R.string.zone_3), zone3Speed)
                else -> Pair(getString(R.string.zone_base), speed)
            }
            currentSpeedText.text = prefix + getString(R.string.speed_zone, zoneSpeed, currentZone)
        } else {
            currentSpeedText.text = prefix + getString(R.string.speed, speed)
        }

        signalText.text = prefix + getString(R.string.signal, signal)

        val batteryFormatted = String.format("%.2f", battery / 100.0)
        batteryText.text = prefix + getString(R.string.battery, batteryFormatted)

        if (isWorking != 2 && isWorking != 6 && isWorking != 9) {
            workTimeText.text = prefix + getString(R.string.work_time, workTime)
            extensionText.text = prefix + getString(R.string.extension, extDouble)
            timeFinishText.text = prefix + getString(R.string.time_to_finish, finishTime)
        }
    }

    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        pobierzUstawieniaStrefowe {
            remoteRepo.getRainHistory(currentRainId, email) { history ->
                if (history.isNotEmpty()) {
                    val latest = history[0]

                    lastStmTimeStr = latest.updatedAt
                    currentIsOffline = false
                    isMachineWorking = (latest.isWorking == 2 || latest.isWorking == 6 || latest.isWorking == 9)

                    baseWorkTimeSec = parseTimeStrToSeconds(latest.workTime)
                    baseExtension = latest.extension.toString().toDoubleOrNull() ?: 0.0
                    baseSpeed = latest.currentSpeed
                    setSpeed = latest.setSpeed

                    // POPRAWKA: Aktualizujemy timestamp dla tickera z najświeższych danych!
                    try {
                        latest.updatedAt?.let { timestampStr ->
                            val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val date = dbFormat.parse(timestampStr)
                            if (date != null) {
                                lastDataTimestamp = date.time
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TICKER_ERROR", "Błąd parsowania czasu dla tickera: ${e.message}")
                    }

                    refreshStatusUI(
                        latest.isWorking, latest.currentSpeed, latest.timeToFinish,
                        latest.workTime, latest.extension.toString(), currentIsOffline, latest.signalStrength, latest.battery
                    )

                    CacheHelper.saveObject(this, "RAIN_LIVE_STATUS_$currentRainId", latest)

                    tickerHandler.removeCallbacks(liveTickerRunnable)
                    if (isMachineWorking) {
                        tickerHandler.post(liveTickerRunnable)
                    }
                } else {
                    tickerHandler.removeCallbacks(liveTickerRunnable)
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

            supportActionBar?.title = getString(R.string.offline_title_format, it.name)
        }

        remoteRepo.getStmUpdateInfo(currentRainId, email) { czasStm, opoznienieAktualizacji ->
            if (czasStm != null && opoznienieAktualizacji != null) {
                val delay = opoznienieAktualizacji.toIntOrNull() ?: 0
                runOnUiThread {
                    displayUpdateTimes(czasStm, delay)
                }
                delayEdit.setText(delay.toString())
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
                    Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun saveDelayData() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        val delayTime = delayEdit.text.toString().trim()
        if (delayTime.isNotEmpty()) {
            remoteRepo.saveNewDelay(email, currentRainId, delayTime) { successDelay ->
                runOnUiThread {
                    if (successDelay) {
                        CacheHelper.saveObject(this@RainDetailsActivity, "RAIN_DETAILS_$currentRainId", delayTime)
                        Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun displayUpdateTimes(lastUpdateStr: String?, delayMinutes: Int) {
        if (lastUpdateStr.isNullOrEmpty() || lastUpdateStr == "null") return

        val safeDelayMinutes = if (delayMinutes > 0) delayMinutes else 1

        try {
            val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

            val lastDate = dbFormat.parse(lastUpdateStr) ?: return
            lastDataTimestamp = lastDate.time

            val currentTime = Calendar.getInstance().time
            tvLastUpdate.text = getString(R.string.last_update, displayFormat.format(lastDate))

            val calendar = Calendar.getInstance()
            calendar.time = lastDate
            calendar.add(Calendar.MINUTE, safeDelayMinutes)
            val nextDate = calendar.time

            val diffMillis = currentTime.time - nextDate.time
            val diffMinutes = diffMillis / (1000 * 60)

            val formattedNextDate = displayFormat.format(nextDate)

            when {
                diffMinutes > safeDelayMinutes * 2L -> {
                    tvNextUpdate.setTextColor(Color.RED)
                    tvNextUpdate.text = getString(R.string.next_update, formattedNextDate) + "\n" + getString(R.string.connection_lost)
                }
                diffMinutes > 0 -> {
                    tvNextUpdate.setTextColor(Color.DKGRAY)
                    tvNextUpdate.text = getString(R.string.next_update, formattedNextDate)
                }
                else -> {
                    tvNextUpdate.setTextColor(Color.GRAY)
                    tvNextUpdate.text = getString(R.string.next_update, formattedNextDate)
                }
            }

        } catch (e: Exception) {
            Log.e("TIME_ERROR", "Błąd formatowania daty: ${e.message}")
        }
    }

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
        tickerHandler.removeCallbacks(liveTickerRunnable)
    }
}