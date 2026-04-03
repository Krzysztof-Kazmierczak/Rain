package com.example.bazadanych.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class RainDetailsActivity : AppCompatActivity() {

    private lateinit var currentRainId: String
    private val remoteRepo = RainRemoteRepository()

    private lateinit var nameEdit: EditText
    private lateinit var lengthEdit: EditText
    private lateinit var commentEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var currentSpeedText: TextView
    private lateinit var timeFinishText: TextView
    private lateinit var map: org.osmdroid.views.MapView
    private lateinit var btnStartDrawing: Button
    private lateinit var btnUndo: Button

    private var machineMarker: Marker? = null
    private val fieldPoints = mutableListOf<GeoPoint>()
    private var currentPolygon = Polygon()
    private var isDrawingMode = false
    private var currentPolygonColor = "#604CAF50"
    private var wasMapCenteredOnMachine = false // Pomoże nam uniknąć "wyrywania" mapy użytkownikowi


    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadLiveStatus()
            refreshHandler.postDelayed(this, 30000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rain_details)

        currentRainId = intent.getStringExtra("id") ?: ""

        Configuration.getInstance().userAgentValue = packageName
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        setupMapClickEvents()

        map.addOnFirstLayoutListener { v, left, top, right, bottom ->
            // Ten kod wykona się TYLKO RAZ, gdy mapa będzie gotowa do działania.

            // Jeśli użytkownik nie rysuje pola, ustawiamy domyślny widok
            if (!isDrawingMode) {
                // Geometryczny środek Polski (okolice Piątku / Kutna)
                val centerOfPoland = GeoPoint(52.0689, 19.4797)

                // Najpierw ustawiamy środek, POTEM zoom
                map.controller.setCenter(centerOfPoland)

                // Zoom 6.5 (lub 7.0) daje ładny widok na cały kraj
                map.controller.setZoom(6.5)

                // Odświeżamy mapę, żeby zmiany były widoczne
                map.invalidate()
            }
        }

        initViews()

        if (currentRainId.isNotEmpty()) {
            loadInitialData()
        } else {
            Toast.makeText(this, "Błąd: Brak ID deszczowni", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
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
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnUndo = findViewById(R.id.btnUndo)

        findViewById<View>(R.id.colorGreen).setOnClickListener { changeFieldColor("#604CAF50") }
        findViewById<View>(R.id.colorYellow).setOnClickListener { changeFieldColor("#60FFEB3B") }
        findViewById<View>(R.id.colorBlue).setOnClickListener { changeFieldColor("#602196F3") }

        btnStartDrawing.setOnClickListener {
            isDrawingMode = !isDrawingMode
            if (isDrawingMode) {
                fieldPoints.clear()
                refreshPolygon()
                btnStartDrawing.text = "ZAKOŃCZ RYSOWANIE"
                btnStartDrawing.setBackgroundColor(Color.parseColor("#F44336")) // Czerwony podczas rysowania
                Toast.makeText(this, "Tryb rysowania aktywny", Toast.LENGTH_SHORT).show()
            } else {
                btnStartDrawing.text = "EDYTUJ POLE"
                btnStartDrawing.setBackgroundColor(Color.parseColor("#4CAF50"))
                saveFieldToDatabase()
            }
        }

        btnUndo.setOnClickListener {
            if (isDrawingMode && fieldPoints.isNotEmpty()) {
                fieldPoints.removeAt(fieldPoints.size - 1)
                refreshPolygon()
            }
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveMainData()
        }

        findViewById<Button>(R.id.deleteButton).setOnClickListener {
            deleteEntry()
        }
    }

    private fun loadInitialData() {
        remoteRepo.getRainDetails(currentRainId) { rain ->
            runOnUiThread {
                if (rain != null) {
                    nameEdit.setText(rain.name)
                    lengthEdit.setText(rain.hoseLength)
                    commentEdit.setText(rain.comment)
                } else {
                    Toast.makeText(this@RainDetailsActivity, "Nie udało się pobrać danych z bazy", Toast.LENGTH_SHORT).show()
                }
            }
        }
        loadAndDrawSavedField()
    }

    private fun setupMapClickEvents() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (isDrawingMode && p != null) {
                    fieldPoints.add(p)
                    refreshPolygon()
                    return true
                }
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun refreshPolygon() {
        map.overlays.remove(currentPolygon)
        if (fieldPoints.isNotEmpty()) {
            currentPolygon = Polygon().apply {
                points = fieldPoints
                fillPaint.color = Color.parseColor(currentPolygonColor)
                outlinePaint.color = Color.BLACK
                outlinePaint.strokeWidth = 3f
            }
            map.overlays.add(currentPolygon)
        }
        map.invalidate()
    }

    private fun changeFieldColor(colorHex: String) {
        currentPolygonColor = colorHex
        if (fieldPoints.isNotEmpty()) {
            currentPolygon.fillPaint.color = Color.parseColor(colorHex)
            map.invalidate()
        }
    }

    private fun saveFieldToDatabase() {
        if (fieldPoints.isEmpty()) return

        val coordsString = fieldPoints.joinToString(";") { "${it.latitude},${it.longitude}" }
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.saveField(email, currentRainId, currentPolygonColor, coordsString) { success ->
            runOnUiThread {
                if (success) Toast.makeText(this, "Pole zapisane! 💾", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, "Błąd zapisu pola ❌ (Sprawdź Logcat)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAndDrawSavedField() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        remoteRepo.getField(email, currentRainId) { fieldData ->
            runOnUiThread {
                if (fieldData != null && fieldData.coordinates.isNotEmpty()) {
                    val points = fieldData.coordinates.split(";").mapNotNull {
                        val latLng = it.split(",")
                        if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
                    }
                    currentPolygonColor = fieldData.color
                    fieldPoints.clear()
                    fieldPoints.addAll(points)
                    refreshPolygon()
                }
            }
        }
    }

    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return

        remoteRepo.getRainHistory(currentRainId) { history ->
            runOnUiThread {
                if (history.isNotEmpty()) {
                    val latest = history[0] // Pobieramy najnowszy wpis

                    // Aktualizujemy Marker na mapie
                    updateMachineMarker(latest.lat, latest.lng)

                    // Aktualizujemy teksty
                    statusText.text = if (latest.isWorking) "Status: PRACUJE ✅" else "Status: POSTÓJ 🛑"
                    currentSpeedText.text = "Aktualna prędkość: ${latest.currentSpeed} m/h"
                    timeFinishText.text = "Czas do końca: ${latest.timeToFinish}"

                    // Możemy też zmienić kolor statusu w tekście
                    statusText.setTextColor(if (latest.isWorking) Color.GREEN else Color.RED)
                } else {
                    statusText.text = "Status: Brak danych GPS ⚠️"
                }
            }
        }
    }

    private fun updateMachineMarker(lat: Double, lng: Double) {
        val isZeroCoords = (lat == 0.0 && lng == 0.0)
        if (isZeroCoords) return // Jeśli GPS nie wysłał danych, nic nie robimy

        val point = GeoPoint(lat, lng)

        // 1. USUWANIE STAREGO MARKERA (żeby nie zostawały "duchy")
        machineMarker?.let { map.overlays.remove(it) }

        // 2. TWORZENIE NOWEGO MARKERA
        machineMarker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) // Dół ikony w punkt GPS
            title = nameEdit.text.toString()

            // OPCJONALNIE: Własna ikona (jeśli masz w drawable)
            // icon = ContextCompat.getDrawable(this@RainDetailsActivity, R.drawable.ic_moja_maszyna)
        }

        map.overlays.add(machineMarker)

        // 3. INTELIGENTNE CENTROWANIE
        // Centrujemy TYLKO jeśli:
        // a) Nie rysujemy pola
        // b) Robimy to pierwszy raz po wejściu w detale (żeby nie przerywać użytkownikowi przesuwania mapy)
        if (!isDrawingMode && !wasMapCenteredOnMachine) {
            map.controller.setZoom(16.0)
            map.controller.animateTo(point)
            wasMapCenteredOnMachine = true // Odznaczamy, że już raz ustawiliśmy widok na maszynę
        }

        map.invalidate() // Odświeżenie mapy
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
                    Toast.makeText(this, "Zapisano dane deszczowni ✅", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Błąd zapisu danych! (Sprawdź Logcat)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteEntry() {
        remoteRepo.deleteRain(currentRainId) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Usunięto 🗑️", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}