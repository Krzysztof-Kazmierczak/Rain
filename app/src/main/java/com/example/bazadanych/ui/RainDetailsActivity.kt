package com.example.bazadanych.ui

import android.content.Intent
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
import com.example.bazadanych.data.db.FieldEntity
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.calculation.GeoUtils
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class RainDetailsActivity : AppCompatActivity() {

    private lateinit var currentRainId: String
    private val remoteRepo = RainRemoteRepository()
    private val refreshHandler = Handler(Looper.getMainLooper())

    // UI Components
    private lateinit var map: MapView
    private lateinit var nameEdit: EditText
    private lateinit var lengthEdit: EditText
    private lateinit var commentEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var currentSpeedText: TextView
    private lateinit var timeFinishText: TextView
    private lateinit var btnStartDrawing: Button
    private lateinit var btnUndo: Button

    // Map Elements
    private var machineMarker: Marker? = null
    private var drawingPolygon = Polygon()
    private val fieldPoints = mutableListOf<GeoPoint>()

    // State
    private var isDrawingMode = false
    private var currentDrawingColor = "#604CAF50"
    private var wasMapCenteredOnMachine = false

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
        setupMap()
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
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnUndo = findViewById(R.id.btnUndo)

        btnStartDrawing.setOnClickListener { toggleDrawingMode() }

        btnUndo.setOnClickListener {
            if (isDrawingMode && fieldPoints.isNotEmpty()) {
                fieldPoints.removeAt(fieldPoints.size - 1)
                updateDrawingPolygon()
            }
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveMainData()
        }

        findViewById<Button>(R.id.deleteButton).setOnClickListener {
            deleteEntry()
        }
    }

    private fun setupMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (isDrawingMode && p != null) {
                    fieldPoints.add(p)
                    updateDrawingPolygon()
                    return true
                }
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        map.overlays.add(eventsOverlay)

        // Domyślny widok na Polskę przed załadowaniem GPS
        map.controller.setZoom(6.5)
        map.controller.setCenter(GeoPoint(52.0689, 19.4797))
    }

    private fun toggleDrawingMode() {
        isDrawingMode = !isDrawingMode
        if (isDrawingMode) {
            fieldPoints.clear()
            updateDrawingPolygon()
            btnStartDrawing.text = "ZAKOŃCZ RYSOWANIE"
            btnStartDrawing.setBackgroundColor(Color.RED)
            Toast.makeText(this, "Klikaj na mapie, aby obrysować pole", Toast.LENGTH_SHORT).show()
        } else {
            if (fieldPoints.size >= 3) {
                goToFieldEdit()
            } else {
                Toast.makeText(this, "Musisz zaznaczyć min. 3 punkty!", Toast.LENGTH_SHORT).show()
                isDrawingMode = true
            }
        }
    }

    private fun updateDrawingPolygon() {
        map.overlays.remove(drawingPolygon)
        if (fieldPoints.isNotEmpty()) {
            drawingPolygon = Polygon().apply {
                points = fieldPoints
                fillPaint.color = Color.parseColor(currentDrawingColor)
                outlinePaint.color = Color.BLACK
                outlinePaint.strokeWidth = 3f
            }
            map.overlays.add(drawingPolygon)
        }
        map.invalidate()
    }

    private fun goToFieldEdit() {
        val area = GeoUtils.calculateAreaInHectares(fieldPoints)
        val coords = fieldPoints.joinToString(";") { "${it.latitude},${it.longitude}" }

        val intent = Intent(this, FieldEditActivity::class.java).apply {
            putExtra("coords", coords)
            putExtra("area", area)
            putExtra("field_id", "0")
            putExtra("name", "name")        // <-- DODAJ TO
            putExtra("crop", "cropType")    // <-- DODAJ TO
            putExtra("comment", "comment")  // <-- DODAJ TO
            putExtra("color", "color")      // <-- DODAJ TO
        }
        startActivity(intent)

        // Reset interfejsu rysowania
        isDrawingMode = false
        btnStartDrawing.text = "DODAJ POLE"
        btnStartDrawing.setBackgroundColor(Color.parseColor("#4CAF50"))
        fieldPoints.clear()
        updateDrawingPolygon()
    }

    private fun loadInitialData() {
        if (currentRainId.isEmpty()) return

        remoteRepo.getRainDetails(currentRainId) { rain ->
            runOnUiThread {
                rain?.let {
                    nameEdit.setText(it.name)
                    lengthEdit.setText(it.hoseLength)
                    commentEdit.setText(it.comment)
                }
            }
        }
        loadAllAgriculturalFields()
    }

    private fun loadAllAgriculturalFields() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        if (email.isEmpty()) return

        remoteRepo.getAgriculturalFields(email) { fields ->
            runOnUiThread {
                // BEZPIECZNE USUWANIE: Zamiast iteratora, używamy funkcji removeAll
                // Usuwamy tylko te nakładki, które są Polygonami, zostawiając marker maszyny
                map.overlays.removeAll { overlay ->
                    overlay is Polygon && overlay != drawingPolygon
                }

                // Rysujemy pobrane z bazy pola
                fields.forEach { drawFieldOnMap(it) }

                map.invalidate() // Odświeżamy widok mapy
            }
        }
    }

    private fun drawFieldOnMap(field: FieldEntity) {
        val pts = field.coordinates.split(";").mapNotNull {
            val latLng = it.split(",")
            if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
        }

        val poly = Polygon().apply {
            points = pts
            fillPaint.color = Color.parseColor(field.color)
            outlinePaint.color = Color.BLACK
            outlinePaint.strokeWidth = 2f
            title = field.name
            snippet = "Uprawa: ${field.cropType}\nPowierzchnia: ${String.format("%.2f", field.areaHa)} ha"

            setOnClickListener { _, _, _ ->
                val i = Intent(this@RainDetailsActivity, FieldEditActivity::class.java)
                i.putExtra("field_id", field.id.toString())
                i.putExtra("coords", field.coordinates)
                i.putExtra("area", field.areaHa)
                startActivity(i)
                true
            }
        }
        map.overlays.add(poly)
    }

    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return
        remoteRepo.getRainHistory(currentRainId) { history ->
            runOnUiThread {
                if (history.isNotEmpty()) {
                    val latest = history[0]
                    updateMachineMarker(latest.lat, latest.lng)
                    statusText.text = if (latest.isWorking) "Status: PRACUJE ✅" else "Status: STOP 🛑"
                    currentSpeedText.text = "Aktualna prędkość: ${latest.currentSpeed} m/h"
                    timeFinishText.text = "Czas do końca: ${latest.timeToFinish}"
                    statusText.setTextColor(if (latest.isWorking) Color.GREEN else Color.RED)
                } else {
                    statusText.text = "Status: Brak danych GPS ⚠️"
                }
            }
        }
    }

    private fun updateMachineMarker(lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return
        val point = GeoPoint(lat, lng)

        machineMarker?.let { map.overlays.remove(it) }

        machineMarker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = nameEdit.text.toString()
        }

        map.overlays.add(machineMarker)

        if (!isDrawingMode && !wasMapCenteredOnMachine) {
            map.controller.setZoom(16.0)
            map.controller.animateTo(point)
            wasMapCenteredOnMachine = true
        }
        map.invalidate()
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
                    Toast.makeText(this, "Błąd zapisu danych!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteEntry() {
        remoteRepo.deleteRain(currentRainId) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Usunięto pomyślnie 🗑️", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshHandler.post(refreshRunnable)
        loadAllAgriculturalFields() // Odśwież pola po powrocie z FieldEditActivity
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}