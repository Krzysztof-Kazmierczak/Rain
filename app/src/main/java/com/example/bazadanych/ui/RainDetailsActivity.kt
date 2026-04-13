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
import com.example.bazadanych.data.calculation.GeoUtils
import com.example.bazadanych.data.db.FieldItem
import com.example.bazadanych.data.local_db.CacheHelper
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.IOException

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
    //private var drawingPolygon = Polygon()
    private var drawingPolyline = org.osmdroid.views.overlay.Polyline()
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
                updateDrawingLine()
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
                    updateDrawingLine()
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
            updateDrawingLine()
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

    private fun updateDrawingLine() {
        // 1. Usuwamy starą linię z mapy
        map.overlays.remove(drawingPolyline)

        if (fieldPoints.isNotEmpty()) {
            // 2. Tworzymy nową linię
            drawingPolyline = org.osmdroid.views.overlay.Polyline().apply {
                // ZAMIAST: points = fieldPoints
                // UŻYWAMY: setPoints(fieldPoints)
                setPoints(fieldPoints)

                outlinePaint.color = Color.parseColor("#FF2196F3") // Niebieski
                outlinePaint.strokeWidth = 5f
            }
            // 3. Dodajemy nową linię na mapę
            map.overlays.add(drawingPolyline)
        }

        // 4. Odświeżamy mapę
        map.invalidate()
    }

    private fun goToFieldEdit() {
        val area = GeoUtils.calculateAreaInHectares(fieldPoints)
        val coords = fieldPoints.joinToString(";") { "${it.latitude},${it.longitude}" }

        val intent = Intent(this, FieldEditActivity::class.java).apply {
            putExtra("field_id", "0") // Nowe pole
            putExtra("coords", coords)
            putExtra("area", area)

            // Dla nowego pola wysyłamy puste napisy, żeby Hinty (podpowiedzi) działały
            putExtra("name", "")
            putExtra("crop", "")
            putExtra("comment", "")
            putExtra("color", "#604CAF50") // Domyślny zielony
        }
        startActivity(intent)

        // Reset interfejsu rysowania
        isDrawingMode = false
        btnStartDrawing.text = "DODAJ POLE"
        btnStartDrawing.setBackgroundColor(Color.parseColor("#4CAF50"))
        fieldPoints.clear()
        updateDrawingLine()
    }

    private fun loadInitialData() {
        if (currentRainId.isEmpty()) return

        val detailCacheKey = "RAIN_DETAILS_$currentRainId"

        // 1. Próbujemy wczytać pełne detale z cache
        var rainData = CacheHelper.loadObject<Rain>(this, detailCacheKey)

        // 2. Jeśli ich nie ma, bierzemy dane z kafelka (to co było widać na liście)
        if (rainData == null) {
            val homeTiles = CacheHelper.loadList<RainTile>(this, "HOME_TILES_CACHE")
            val tile = homeTiles?.find { it.id == currentRainId }
            if (tile != null) {
                rainData = Rain(tile.id, tile.title, tile.hoseLength, tile.comment, tile.isWorking)
            }
        }

        // 3. WYKORZYSTUJEMY FUNKCJĘ POMOCNICZĄ DLA CACHE
        rainData?.let {
            nameEdit.setText(it.name)
            lengthEdit.setText(it.hoseLength)
            commentEdit.setText(it.comment)

            // Pokazujemy status z pamięci, żeby użytkownik nie widział "Pobieranie..."
            updateUIStatus(it.isWorking)
        }

        // 4. Pobieramy świeże dane techniczne (nazwa, wąż)
        remoteRepo.getRainDetails(currentRainId) { rainFromServer ->
            runOnUiThread {
                rainFromServer?.let {
                    nameEdit.setText(it.name)
                    lengthEdit.setText(it.hoseLength)
                    commentEdit.setText(it.comment)
                    CacheHelper.saveObject(this@RainDetailsActivity, detailCacheKey, it)

                    // Opcjonalnie: jeśli Twoje nowe getRainDetails poprawnie pobiera status,
                    // możesz go tu odświeżyć, ale bezpieczniej zostawić to dla loadLiveStatus
                    updateUIStatus(it.isWorking)
                }
            }
        }
    }

    // Pomocnicza funkcja, żeby kod był czysty
    private fun updateUIStatus(working: Boolean) {
        statusText.text = if (working) "Status: PRACUJE ✅" else "Status: STOP 🛑"
        statusText.setTextColor(if (working) android.graphics.Color.GREEN else android.graphics.Color.RED)
    }

    private fun updateStatusView(isWorking: Boolean, speed: Double?, finishTime: String?, isOffline: Boolean) {
        val label = if (isOffline) "[Cache] " else ""
        statusText.text = "${label}Status: ${if (isWorking) "PRACUJE ✅" else "STOP 🛑"}"
        statusText.setTextColor(if (isWorking) android.graphics.Color.GREEN else android.graphics.Color.RED)

        speed?.let { currentSpeedText.text = "${label}Prędkość: $it m/h" }
        finishTime?.let { timeFinishText.text = "${label}Czas do końca: $it" }
    }

    private fun loadAllAgriculturalFields() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        if (email.isEmpty()) return

        // 1. ŁADOWANIE OFFLINE - zmieniamy <FieldEntity> na <FieldItem>
        val cachedFields = com.example.bazadanych.data.local_db.CacheHelper.loadList<FieldItem>(this, "FIELDS_CACHE")
        if (cachedFields != null && cachedFields.isNotEmpty()) {
            // Usuwamy stare polygony (oprócz linii rysowania)
            map.overlays.removeAll { overlay -> overlay is Polygon && overlay != drawingPolyline }
            cachedFields.forEach { drawFieldOnMap(it) }
            map.invalidate()
        }

        // 2. POBIERANIE Z SIECI - repo zwraca teraz List<FieldItem>
        remoteRepo.getAgriculturalFields(email) { fields ->
            runOnUiThread {
                if (fields.isNotEmpty()) {
                    com.example.bazadanych.data.local_db.CacheHelper.saveList(this@RainDetailsActivity, "FIELDS_CACHE", fields)

                    map.overlays.removeAll { overlay -> overlay is Polygon && overlay != drawingPolyline }
                    fields.forEach { drawFieldOnMap(it) }
                    map.invalidate()
                }
            }
        }
    }

    private fun drawFieldOnMap(field: FieldItem) {
        val pts = field.coordinates.split(";").mapNotNull {
            val latLng = it.split(",")
            if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
        }

        val poly = Polygon().apply {
            points = pts
            fillPaint.color = Color.parseColor(field.color)
            outlinePaint.color = Color.BLACK
            outlinePaint.strokeWidth = 2f

            // Dane do dymka informacyjnego
            title = field.name ?: "Pole bez nazwy"
            snippet = "🌾 Uprawa: ${field.cropType}\n📐 Powierzchnia: ${String.format("%.2f", field.areaHa)} ha\n\n(KLIKNIJ POLE PONOWNIE, ABY EDYTOWAĆ)"

            // Używamy standardowego dymka bez kombinowania z dotykiem
            infoWindow = org.osmdroid.views.overlay.infowindow.BasicInfoWindow(
                org.osmdroid.library.R.layout.bonuspack_bubble, map
            )
        }

        // ZWYKŁE KLIKNIĘCIE W POLE NA MAPIE
        poly.setOnClickListener { polygon, _, _ ->
            if (polygon.isInfoWindowOpen) {
                // JEŚLI DYMEK BYŁ JUŻ OTWARTY - przechodzimy do edycji!
                polygon.closeInfoWindow()

                val intent = Intent(this@RainDetailsActivity, FieldEditActivity::class.java).apply {
                    putExtra("field_id", field.id.toString())
                    putExtra("coords", field.coordinates)
                    putExtra("area", field.areaHa)
                    putExtra("name", field.name)
                    putExtra("crop", field.cropType)
                    putExtra("comment", field.comment)
                    putExtra("color", field.color)
                }
                startActivity(intent)
            } else {
                // JEŚLI DYMEK BYŁ ZAMKNIĘTY - zamykamy inne i otwieramy ten
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(map)
                polygon.showInfoWindow()
            }
            true
        }

        map.overlays.add(poly)
    }
    private fun loadLiveStatus() {
        if (currentRainId.isEmpty()) return

        remoteRepo.getRainHistory(currentRainId) { history ->
            runOnUiThread {
                if (history.isNotEmpty()) {
                    val latest = history[0]

                    // WYKORZYSTUJEMY Twoją drugą funkcję (tą bardziej szczegółową)
                    updateStatusView(
                        isWorking = latest.isWorking,
                        speed = latest.currentSpeed,
                        finishTime = latest.timeToFinish,
                        isOffline = false // Dane są świeżo z serwera
                    )

                    updateMachineMarker(latest.lat, latest.lng)
                } else {
                    // Jeśli nie ma historii, spróbuj chociaż pokazać ostatni znany stan z cache
                    val statusKey = "RAIN_LIVE_STATUS_$currentRainId"
                    val cached = CacheHelper.loadObject<RainStatus>(this, statusKey)
                    cached?.let {
                        updateStatusView(it.isWorking, it.currentSpeed, it.timeToFinish, true)
                    }
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

        // Tworzymy obiekt Rain, ale isWorking nas nie obchodzi przy zapisie danych maszyny
        // Backend powinien aktualizować tylko nazwę, długość i komentarz.
        val rain = Rain(
            id = currentRainId,
            name = nameEdit.text.toString(),
            hoseLength = lengthEdit.text.toString(),
            comment = commentEdit.text.toString(),
            isWorking = false // To pole w save_rain.php powinno być ignorowane
        )

        remoteRepo.saveRain(email, rain) { success ->
            runOnUiThread {
                if (success) {
                    CacheHelper.saveObject(this@RainDetailsActivity, "RAIN_DETAILS_$currentRainId", rain)
                    Toast.makeText(this, "Zapisano dane ✅", Toast.LENGTH_SHORT).show()
                    finish() // Wracamy do Home, gdzie kafelki się odświeżą
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