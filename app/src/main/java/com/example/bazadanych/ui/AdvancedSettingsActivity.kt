package com.example.bazadanych.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import android.text.Editable
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bazadanych.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdvancedSettingsActivity : AppCompatActivity() {
    private val remoteRepo = com.example.bazadanych.data.repository.RainRemoteRepository()
    private lateinit var currentRainId: String
    private var maxHoseLength: Int = 0
    private lateinit var editTargetSpeed: EditText
    private lateinit var cbZoneWatering: CheckBox
    private lateinit var z1End: EditText
    private lateinit var z2End: EditText
    private lateinit var z3End: EditText
    private lateinit var tvZ2Start: TextView
    private lateinit var tvZ3Start: TextView
    private lateinit var editZ1Speed: EditText
    private lateinit var editZ2Speed: EditText
    private lateinit var editZ3Speed: EditText
    private lateinit var btnToggleWork: MaterialButton
    private var isMachineWorking: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        // Pobranie danych z Intent
        currentRainId = intent.getStringExtra("id") ?: ""
        val lengthStr = intent.getStringExtra("max_hose_length") ?: ""
        maxHoseLength = lengthStr.toIntOrNull() ?: 600

        setupToolbar()
        initViewReferences()
        setupUI()
        setupZoneLogic()
        setupInfoButtons()

        loadDataFromServer()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarAdvanced)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initViewReferences() {
        btnToggleWork = findViewById(R.id.btnToggleWork)

        editTargetSpeed = findViewById(R.id.editTargetSpeed)
        cbZoneWatering = findViewById(R.id.cbZoneWatering)
        z1End = findViewById(R.id.editZ1End)
        z2End = findViewById(R.id.editZ2End)
        z3End = findViewById(R.id.editZ3End)
        tvZ2Start = findViewById(R.id.tvZ2Start)
        tvZ3Start = findViewById(R.id.tvZ3Start)
        editZ1Speed = findViewById(R.id.editZ1Speed)
        editZ2Speed = findViewById(R.id.editZ2Speed)
        editZ3Speed = findViewById(R.id.editZ3Speed)
    }

    private fun setupUI() {
        // Konfiguracja pól czasu
        setupDelayField(R.id.cbDelayStart, R.id.editDelayStart)
        setupDelayField(R.id.cbDelayWind, R.id.editDelayWind)
        setupDelayField(R.id.cbDelayEnd, R.id.editDelayEnd)

        findViewById<MaterialButton>(R.id.btnSaveAdvanced).setOnClickListener {
            saveDataToServer()
        }

        findViewById<MaterialButton>(R.id.btnDeleteMachine).setOnClickListener {
            confirmDeleteMachine()
        }

        findViewById<MaterialButton>(R.id.btnDeleteLocalization).setOnClickListener {
            deleteLocalization()
        }

        btnToggleWork.setOnClickListener {
            val newStatus = if (isMachineWorking == 1) 0 else 1
            toggleMachineWork(newStatus)
        }
    }

    private fun updateWorkStatusUI(isWorking: Int) {
        isMachineWorking = isWorking

        if (isWorking == 2) {
            btnToggleWork.text = "ZATRZYMAJ PRACĘ"
            btnToggleWork.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F44336")
                ) // Czerwony
            btnToggleWork.setIconResource(android.R.drawable.ic_media_pause)
            btnToggleWork.isEnabled = true

        } else if (isWorking == 1) {
            btnToggleWork.text = "URUCHOM PRACĘ"
            btnToggleWork.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50")
                ) // Zielony
            btnToggleWork.setIconResource(android.R.drawable.ic_media_play)
            btnToggleWork.isEnabled = true

        } else {
            btnToggleWork.text = "BRAK ŁĄCZNOŚCI Z URZĄDZENIEM"
            btnToggleWork.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#9E9E9E")
                ) // Szary
            btnToggleWork.setIconResource(android.R.drawable.ic_dialog_alert)
            btnToggleWork.isEnabled = false
        }
    }

    private fun toggleMachineWork(newStatus: Int) {
        val userEmail = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        // Pokazujemy, że coś się dzieje
        btnToggleWork.isEnabled = false
        btnToggleWork.text = "PROSZĘ CZEKAĆ..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Przykładowy endpoint do zmiany statusu
                val url = URL("https://rain-tech.pl/android/update_work_status.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "id=$currentRainId&email=$userEmail&status=$newStatus"
                OutputStreamWriter(conn.outputStream).use { it.write(postData) }

                val response = conn.inputStream.bufferedReader().readText().trim()

                withContext(Dispatchers.Main) {
                    btnToggleWork.isEnabled = true
                    if (response == "OK") {
                        updateWorkStatusUI(newStatus)
                        Toast.makeText(this@AdvancedSettingsActivity, "Zmieniono status pracy", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@AdvancedSettingsActivity, "Błąd serwera: $response", Toast.LENGTH_SHORT).show()
                        loadDataFromServer() // Odświeżamy dane, by wrócić do realnego stanu
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnToggleWork.isEnabled = true
                    Toast.makeText(this@AdvancedSettingsActivity, "Błąd połączenia", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDeleteMachine() {
        val userEmail = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        AlertDialog.Builder(this)
            .setTitle("USUWANIE MASZYNY")
            .setMessage("Czy na pewno chcesz CAŁKOWICIE usunąć tę maszynę z systemu? Tej operacji nie można cofnąć.")
            .setPositiveButton("USUŃ") { _, _ ->
                performDeleteMachine(currentRainId, userEmail)
            }
            .setNegativeButton("Anuluj", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun performDeleteMachine(rainId: String, userEmail: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://rain-tech.PL/android/delete_rain.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "id=$rainId&email=$userEmail"

                OutputStreamWriter(conn.outputStream).use { it.write(postData) }

                val responseCode = conn.responseCode
                val response = conn.inputStream.bufferedReader().readText().trim()

                withContext(Dispatchers.Main) {
                    if (responseCode == 200 && response == "OK") {
                        Toast.makeText(this@AdvancedSettingsActivity, "Maszyna została usunięta", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@AdvancedSettingsActivity, HomeActivity::class.java)

                        // Flagi: Czyścimy stos, aby HomeActivity była "na górze", a poprzednie ekrany usunięte
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK

                        startActivity(intent)
                        finish() // Zamykamy AdvancedSettingsActivity
                    } else {
                        Log.e("DELETE_ERROR", "Serwer zwrócił: $response")
                        Toast.makeText(this@AdvancedSettingsActivity, "Błąd: $response", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdvancedSettingsActivity, "Błąd połączenia: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteLocalization() {
        val userEmail = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        val rainId = intent.getStringExtra("id") ?: ""

        // Wyświetlamy proste pytanie przed usunięciem
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Usuwanie lokalizacji")
            .setMessage("Czy na pewno chcesz zresetować pozycję tej maszyny? Przestanie być widoczna na mapie.")
            .setPositiveButton("Tak, usuń") { _, _ ->
                performDelete(rainId, userEmail)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // Przeniosłem logikę do osobnej funkcji dla czystości kodu
    private fun performDelete(rainId: String, userEmail: String) {
        if (rainId.isNotEmpty() && userEmail.isNotEmpty()) {
            remoteRepo.updateRainManualLocation(rainId, userEmail, 0.0, 0.0) { success ->
                runOnUiThread {
                    if (success) {
                        // --- TO DODAJEMY: ---
                        // Musimy wyczyścić cache historii dla tej konkretnej maszyny
                        val historyKey = "HISTORY_$rainId"
                        val sharedPrefs = getSharedPreferences("CachePrefs", MODE_PRIVATE) // Upewnij się, że nazwa SP pasuje do CacheHelper
                        sharedPrefs.edit().remove(historyKey).apply()
                        // --------------------

                        Toast.makeText(this, "Lokalizacja zresetowana", Toast.LENGTH_SHORT).show()

                        //val intent = Intent(this, HomeActivity::class.java)
                       // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                      //  startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun setupZoneLogic() {
        val containerZones = findViewById<LinearLayout>(R.id.containerZones)

        cbZoneWatering.setOnCheckedChangeListener { _, isChecked ->
            containerZones.visibility = if (isChecked) View.VISIBLE else View.GONE
            editTargetSpeed.isEnabled = !isChecked
            editTargetSpeed.alpha = if (isChecked) 0.5f else 1.0f

            // Autouzupełnianie przy pierwszym włączeniu
            if (isChecked && z1End.text.isEmpty()) {
                applyDefaultZones()
            }
        }

        // --- SYNCHRONIZACJA "POPYCHANIE" ---
        z1End.doAfterTextChanged { s ->
            val y = s.toIntOrNull() ?: 0
            tvZ2Start.text = y.toString()
            val z = z2End.text.toString().toIntOrNull() ?: 0
            if (y >= z) z2End.setText((y + 1).toString())
        }

        z2End.doAfterTextChanged { s ->
            val z = s.toIntOrNull() ?: 0
            tvZ3Start.text = z.toString()
            val a = z3End.text.toString().toIntOrNull() ?: 0
            if (z >= a) z3End.setText((z + 1).toString())
        }

        z3End.doAfterTextChanged { s ->
            val a = s.toIntOrNull() ?: 0
            if (a > maxHoseLength) {
                z3End.setText(maxHoseLength.toString())
                Toast.makeText(this, "Maksymalna długość węża: $maxHoseLength m", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyDefaultZones() {
        val segment = (maxHoseLength / 3)
        val rounded = (Math.round(segment / 50.0) * 50).toInt().coerceAtLeast(50)

        z1End.setText(rounded.toString())
        z2End.setText((rounded * 2).toString())
        z3End.setText(maxHoseLength.toString())
    }

    // --- LOGIKA CZASU (NumberPicker) ---
    private fun setupDelayField(checkBoxId: Int, editTextId: Int) {
        val cb = findViewById<CheckBox>(checkBoxId)
        val et = findViewById<EditText>(editTextId)

        cb.setOnCheckedChangeListener { _, isChecked ->
            et.isEnabled = isChecked
            if (!isChecked) {
                et.setText("")
            } else {
                // Pokazuj dialog TYLKO jeśli CheckBox został zaznaczony, a pole tekstowe jest puste
                // i (opcjonalnie) użytkownik dotknął ekranu (ma fokus)
                if (et.text.isEmpty()) {
                    showTimePickerDialog(et)
                }
            }
        }
        et.setOnClickListener { if (cb.isChecked) showTimePickerDialog(et) }
    }

    private fun showTimePickerDialog(editText: EditText) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val npH = dialogView.findViewById<NumberPicker>(R.id.npHours).apply { minValue = 0; maxValue = 99 }
        val npM = dialogView.findViewById<NumberPicker>(R.id.npMinutes).apply { minValue = 0; maxValue = 59 }
        val npS = dialogView.findViewById<NumberPicker>(R.id.npSeconds).apply { minValue = 0; maxValue = 59 }

        AlertDialog.Builder(this)
            .setTitle("Ustaw czas")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                editText.setText(String.format("%02d:%02d:%02d", npH.value, npM.value, npS.value))
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // --- SERWER ---
    private fun loadDataFromServer() {
        // 1. Pobieramy email z SharedPreferences
        val userEmail = getSharedPreferences("user_session", MODE_PRIVATE)
            .getString("user_email", "") ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 2. Dodajemy email do adresu URL
                val urlString = "https://rain-tech.PL/android/get_rain_adv.php?id=$currentRainId&email=$userEmail"
                val url = URL(urlString)

                Log.d("DEBUG_ADV", "Wywołuję URL: $urlString")

                val response = url.readText()
                val json = JSONObject(response)

                withContext(Dispatchers.Main) {
                    editTargetSpeed.setText(json.optString("target_speed"))

                    // Ładowanie stref
                    cbZoneWatering.isChecked = json.optInt("zone_watering") == 1
                    z1End.setText(json.optString("z1_end"))
                    z2End.setText(json.optString("z2_end"))
                    z3End.setText(json.optString("z3_end"))

                    editZ1Speed.setText(json.optString("z1_speed"))
                    editZ2Speed.setText(json.optString("z2_speed"))
                    editZ3Speed.setText(json.optString("z3_speed"))

                    // Ładowanie opóźnień
                    checkAndSetDelay(R.id.cbDelayStart, R.id.editDelayStart, json.optString("delay_start"))
                    checkAndSetDelay(R.id.cbDelayWind, R.id.editDelayWind, json.optString("delay_wind"))
                    checkAndSetDelay(R.id.cbDelayEnd, R.id.editDelayEnd, json.optString("delay_end"))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        remoteRepo.getRains(userEmail) { rains ->
            val currentRain = rains.find { it.id == currentRainId }
            runOnUiThread {
                if (currentRain != null) {
                    updateWorkStatusUI(currentRain.isWorking)
                }
            }
        }
    }

    private fun checkAndSetDelay(cbId: Int, etId: Int, value: String) {
        if (value.isNotEmpty() && value != "00:00:00") {
            val et = findViewById<EditText>(etId)
            val cb = findViewById<CheckBox>(cbId)

            et.setText(value) // 1. Najpierw ustawiamy tekst
            cb.isChecked = true // 2. Potem zaznaczamy - listener sprawdzi et.text i zobaczy, że nie jest puste
        }
    }

    private fun saveDataToServer() {
        // 1. POBIERZ EMAIL Z SESJI
        val userEmail = getSharedPreferences("user_session", MODE_PRIVATE)
            .getString("user_email", "") ?: ""

        // 2. DODAJ EMAIL DO JSONA
        val jsonParams = JSONObject().apply {
            put("id", currentRainId)
            put("email", userEmail) // <--- TEGO BRAKOWAŁO!
            put("target_speed", editTargetSpeed.text.toString().ifEmpty { "0" })
            put("zone_watering", if (cbZoneWatering.isChecked) 1 else 0)
            put("z1_end", z1End.text.toString().ifEmpty { "0" })
            put("z2_end", z2End.text.toString().ifEmpty { "0" })
            put("z3_end", z3End.text.toString().ifEmpty { "0" })
            put("z1_speed", editZ1Speed.text.toString().ifEmpty { "0" })
            put("z2_speed", editZ2Speed.text.toString().ifEmpty { "0" })
            put("z3_speed", editZ3Speed.text.toString().ifEmpty { "0" })
            put("delay_start", findViewById<EditText>(R.id.editDelayStart).text.toString().ifEmpty { "00:00:00" })
            put("delay_wind", findViewById<EditText>(R.id.editDelayWind).text.toString().ifEmpty { "00:00:00" })
            put("delay_end", findViewById<EditText>(R.id.editDelayEnd).text.toString().ifEmpty { "00:00:00" })
        }

        android.util.Log.d("DEBUG_SAVE", "Wysyłam JSON: $jsonParams")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://rain-tech.PL/android/save_rain_adv.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000 // 5 sekund na połączenie

                OutputStreamWriter(conn.outputStream).use { it.write(jsonParams.toString()) }

                val responseCode = conn.responseCode

                if (responseCode == 200) {
                    // Czytamy odpowiedź sukcesu (opcjonalnie)
                    val response = conn.inputStream.bufferedReader().readText()
                    android.util.Log.d("DEBUG_SAVE", "Sukces serwera: $response")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdvancedSettingsActivity, "Zapisano! ✅", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    // TUTAJ KLUCZ: Czytamy co serwer wyrzucił jako błąd (np. błąd SQL z PHP)
                    val errorResponse = conn.errorStream?.bufferedReader()?.readText() ?: "Brak szczegółów błędu"
                    android.util.Log.e("DEBUG_SAVE", "Błąd serwera ($responseCode): $errorResponse")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdvancedSettingsActivity, "Błąd serwera: $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("DEBUG_SAVE", "Wyjątek: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdvancedSettingsActivity, "Błąd połączenia: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupInfoButtons() {
        fun setInfo(btnId: Int, message: String) {
            findViewById<ImageButton>(btnId).setOnClickListener {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        setInfo(R.id.btnInfoSpeed, "Zadana Prędkość m/h")
        setInfo(R.id.btnInfoDelayStart, "Opóźnienie Startu (czas) h/m/s")
        setInfo(R.id.btnInfoDelayWind, "Opóźnienie Rozpoczęcia zwijania (czas) h/m/s")
        setInfo(R.id.btnInfoDelayEnd, "Opóźnienie Zakończenia podlewania (czas) h/m/s")
        setInfo(R.id.btnInfoZone, "Funkcja Podlewanie strefowe")
    }

    // Pomocnicze rozszerzenie
    private fun EditText.doAfterTextChanged(action: (String) -> Unit) {
        this.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: Editable?) { // <--- Tutaj musi być Editable?
                action(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}