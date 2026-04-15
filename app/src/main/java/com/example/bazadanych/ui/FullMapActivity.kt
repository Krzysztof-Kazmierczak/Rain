package com.example.bazadanych.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.calculation.GeoUtils
import com.example.bazadanych.data.db.FieldItem
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.local_db.CacheHelper
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow

class FullMapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private val remoteRepo = RainRemoteRepository()
    private var isDrawingMode = false
    private var drawingPolyline = Polyline()
    private val fieldPoints = mutableListOf<GeoPoint>()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnStartDrawing: Button
    private lateinit var btnUndo: Button

    private val MAP_CONFIG_KEY = "LAST_MAP_CONFIG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val conf = org.osmdroid.config.Configuration.getInstance()
        val ctx = applicationContext

        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        conf.userAgentValue = packageName
        conf.tileFileSystemCacheMaxBytes = 1000L * 1024L * 1024L

        // NAPRAWIONY BLOK:
        // Używamy wartości 1 (pozwolenie na bulk) i 0 (brak restrykcji flagowych)
// To ominie problem z brakującymi nazwami w bibliotece
        val bulkPolicy = org.osmdroid.tileprovider.tilesource.TileSourcePolicy(1, 0)

        val mapnikWithBulk = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "MapnikBulk",
            0, 19, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.org/",
                "https://b.tile.openstreetmap.org/",
                "https://c.tile.openstreetmap.org/"
            ),
            "© OpenStreetMap contributors",
            bulkPolicy
        )


        setContentView(R.layout.activity_full_map)

        map = findViewById(R.id.fullMap)
        map.setTileSource(mapnikWithBulk)
        map.setMultiTouchControls(true)

        setupToolbar()
        setupMap()
        initUI()

        // 5. Przywracanie pozycji z pamięci
        val lastConfig = CacheHelper.loadObject<CacheHelper.MapConfig>(this, MAP_CONFIG_KEY)
        if (lastConfig != null) {
            map.controller.setZoom(lastConfig.zoom)
            map.controller.setCenter(GeoPoint(lastConfig.lat, lastConfig.lng))
        } else {
            map.controller.setZoom(6.0)
            map.controller.setCenter(GeoPoint(52.0, 19.0))
        }

        loadData()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.mapDrawerLayout)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupMap() {
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (isDrawingMode && p != null) {
                    fieldPoints.add(p)
                    btnUndo.visibility = View.VISIBLE
                    updateDrawingLine()
                    return true
                }
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(map)
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        map.overlays.add(eventsOverlay)
    }

    private fun initUI() {
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnUndo = findViewById(R.id.btnUndo)

        btnStartDrawing.setOnClickListener { toggleDrawingMode() }
        btnUndo.setOnClickListener {
            if (fieldPoints.isNotEmpty()) {
                fieldPoints.removeAt(fieldPoints.size - 1)
                if (fieldPoints.isEmpty()) {
                    resetDrawingState()
                }
                updateDrawingLine()
            }
        }

        findViewById<FloatingActionButton>(R.id.btnCenterAll).setOnClickListener { loadData() }

        findViewById<Button>(R.id.menuRainsHeader).setOnClickListener {
            val rec = findViewById<RecyclerView>(R.id.recyclerRains)
            rec.visibility = if (rec.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.menuFieldsHeader).setOnClickListener {
            val rec = findViewById<RecyclerView>(R.id.recyclerFields)
            rec.visibility = if (rec.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<LinearLayout>(R.id.btnBackToMainMenu).setOnClickListener { finish() }
    }

    // ... DALEJ MASZ loadData I RESZTĘ METOD, KTÓRE SĄ OK ...



    private fun drawFieldOnMap(field: FieldItem) {
        val pts = field.coordinates.split(";").mapNotNull {
            val latLng = it.split(",")
            // Bezpieczniejsza wersja:
            if (latLng.size == 2) {
                val lat = latLng[0].toDoubleOrNull() ?: 0.0
                val lng = latLng[1].toDoubleOrNull() ?: 0.0
                GeoPoint(lat, lng)
            } else null
        }

        val poly = Polygon(map).apply {
            points = pts
            fillPaint.color = Color.parseColor(field.color)
            fillPaint.alpha = 100 // Półprzezroczystość
            outlinePaint.color = Color.BLACK
            outlinePaint.strokeWidth = 2f
            title = field.name ?: "Pole"

            snippet = """
        Uprawa: ${field.cropType}<br>
        Powierzchnia: ${String.format("%.2f", field.areaHa)} ha<br><br>
        <small><i>Kliknij ponownie aby wejść w szczegóły</i></small>
    """.trimIndent()

            infoWindow = BasicInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, map)
        }

        poly.setOnClickListener { polygon, _, _ ->
            if (polygon.isInfoWindowOpen) {
                val intent = Intent(this, FieldEditActivity::class.java).apply {
                    putExtra("field_id", field.id.toString())
                    putExtra("coords", field.coordinates)
                    putExtra("area", field.areaHa)
                    putExtra("name", field.name)
                    putExtra("crop", field.cropType)
                    putExtra("color", field.color)
                }
                startActivity(intent)
            } else {
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(map)
                polygon.showInfoWindow()
            }
            true
        }
        map.overlays.add(poly)
    }

    private fun addRainMarker(rain: Rain) {
        val HISTORY_KEY = "HISTORY_${rain.id}"

        Thread {
            // 1. Sprawdzanie cache w TLE
            val cachedHistory = CacheHelper.loadList<com.example.bazadanych.data.db.RainStatus>(this, HISTORY_KEY)

            if (cachedHistory != null && cachedHistory.isNotEmpty()) {
                runOnUiThread {
                    drawRainMarkerOnMap(rain, cachedHistory[0].lat, cachedHistory[0].lng)
                }
            }

            // 2. Pobieranie online
            remoteRepo.getRainHistory(rain.id) { history ->
                if (history.isNotEmpty()) {
                    CacheHelper.saveList(this, HISTORY_KEY, history)
                    runOnUiThread {
                        drawRainMarkerOnMap(rain, history[0].lat, history[0].lng)
                    }
                }
            }
        }.start()
    }

    // Pomocnicza funkcja, żeby nie powtarzać kodu rysowania markera
    private fun drawRainMarkerOnMap(rain: Rain, lat: Double, lng: Double) {
        // Usuń stary marker tej maszyny, jeśli istnieje (żeby się nie dublowały po odświeżeniu)
        map.overlays.removeAll { it is Marker && it.title == rain.name }

        val marker = Marker(map).apply {
            position = GeoPoint(lat, lng)
            title = rain.name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { m, _ ->
                if (m.isInfoWindowOpen) {
                    startActivity(Intent(this@FullMapActivity, RainDetailsActivity::class.java).putExtra("id", rain.id))
                } else m.showInfoWindow()
                true
            }
        }
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun toggleDrawingMode() {
        isDrawingMode = !isDrawingMode
        if (isDrawingMode) {
            fieldPoints.clear()
            btnStartDrawing.text = "ZAKOŃCZ POLE"
            btnUndo.visibility = View.VISIBLE
        } else {
            if (fieldPoints.size >= 3) {
                goToFieldEdit()
            } else {
                Toast.makeText(this, "Zaznacz min. 3 punkty!", Toast.LENGTH_SHORT).show()
                isDrawingMode = true
            }
        }
    }

    private fun resetDrawingState() {
        isDrawingMode = false
        btnStartDrawing.text = "DODAJ POLE"
        btnUndo.visibility = View.GONE
        fieldPoints.clear()
    }

    private fun updateDrawingLine() {
        map.overlays.remove(drawingPolyline)
        if (fieldPoints.isNotEmpty()) {
            drawingPolyline = Polyline().apply {
                setPoints(fieldPoints)
                outlinePaint.color = Color.BLUE
                outlinePaint.strokeWidth = 5f
            }
            map.overlays.add(drawingPolyline)
        }
        map.invalidate()
    }

    private fun goToFieldEdit() {
        val area = GeoUtils.calculateAreaInHectares(fieldPoints)
        val coords = fieldPoints.joinToString(";") { "${it.latitude},${it.longitude}" }

        val intent = Intent(this, FieldEditActivity::class.java).apply {
            putExtra("field_id", "0")
            putExtra("coords", coords)
            putExtra("area", area)
            putExtra("color", "#604CAF50")
        }
        startActivity(intent)

        resetDrawingState()
        updateDrawingLine()
    }

    private fun centerMapOnFields(fields: List<FieldItem>) {
        val allPoints = mutableListOf<GeoPoint>()
        fields.forEach { field ->
            field.coordinates.split(";").forEach {
                val latLng = it.split(",")
                if (latLng.size == 2) allPoints.add(GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()))
            }
        }
        if (allPoints.isNotEmpty()) {
            val box = BoundingBox.fromGeoPoints(allPoints)

            // Zapisujemy środek tego boxa do pamięci konfiguracji
            val center = box.centerWithDateLine
            val config = CacheHelper.MapConfig(center.latitude, center.longitude, 14.0)
            CacheHelper.saveObject(this, MAP_CONFIG_KEY, config)

            map.zoomToBoundingBox(box, true, 150)
        }
    }

    private fun setupFieldsSidebar(fields: List<FieldItem>) {
        val items = fields.mapNotNull { field ->
            val parts = field.coordinates.split(";")
            if (parts.isNotEmpty()) {
                val allPoints = parts.mapNotNull { p ->
                    val latLng = p.split(",")
                    if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
                }

                if (allPoints.isNotEmpty()) {
                    // OBLICZANIE ŚRODKA: Wyciągamy średnią ze wszystkich szerokości i długości
                    val centerLat = allPoints.map { it.latitude }.average()
                    val centerLng = allPoints.map { it.longitude }.average()

                    MapSidebarAdapter.SidebarItem(
                        field.id.toString(),
                        field.name ?: "Pole",
                        centerLat,
                        centerLng
                    )
                } else null
            } else null
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerFields)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = MapSidebarAdapter(items) { item ->
            // 1. Znajdujemy pole w liście po ID
            val field = fields.find { it.id.toString() == item.id }

            if (field != null) {
                val pts = field.coordinates.split(";").mapNotNull {
                    val latLng = it.split(",")
                    if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
                }

                if (pts.isNotEmpty()) {
                    val box = BoundingBox.fromGeoPoints(pts)
                    // Przybliż do granic pola z marginesem 150 pikseli
                    map.zoomToBoundingBox(box, true, 150)
                }
            }
            drawerLayout.closeDrawers()
        }
    }

    private fun setupRainsSidebar(rains: List<Rain>) {
        val recycler = findViewById<RecyclerView>(R.id.recyclerRains)
        recycler.layoutManager = LinearLayoutManager(this)

        // Tworzymy listę i adapter na starcie
        val sidebarItems = mutableListOf<MapSidebarAdapter.SidebarItem>()
        val adapter = MapSidebarAdapter(sidebarItems) { item ->
            if (item.lat == 0.0 && item.lng == 0.0) {
                Toast.makeText(this, "Brak sygnału GPS", Toast.LENGTH_SHORT).show()
            } else {
                map.controller.animateTo(GeoPoint(item.lat, item.lng))
                map.controller.setZoom(18.0)
                drawerLayout.closeDrawers()
            }
        }
        recycler.adapter = adapter

        rains.forEach { rain ->
            val HISTORY_KEY = "HISTORY_${rain.id}"

            Thread {
                // 1. NAJPIERW PRÓBUJEMY WYCIĄGNĄĆ POZYCJĘ Z CACHE
                val cachedHistory = CacheHelper.loadList<com.example.bazadanych.data.db.RainStatus>(this, HISTORY_KEY)
                if (cachedHistory != null && cachedHistory.isNotEmpty()) {
                    val cachedItem = MapSidebarAdapter.SidebarItem(
                        rain.id,
                        rain.name,
                        cachedHistory[0].lat,
                        cachedHistory[0].lng
                    )

                    runOnUiThread {
                        // Dodaj tylko jeśli jeszcze nie ma tej maszyny na liście
                        if (!sidebarItems.any { it.id == cachedItem.id }) {
                            sidebarItems.add(cachedItem)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }

                // 2. POTEM (JEŚLI JEST NET) PRÓBUJEMY ODŚWIEŻYĆ Z SERWERA
                remoteRepo.getRainHistory(rain.id) { history ->
                    if (history.isNotEmpty()) {
                        val newItem = MapSidebarAdapter.SidebarItem(
                            rain.id,
                            rain.name,
                            history[0].lat,
                            history[0].lng
                        )

                        runOnUiThread {
                            // Szukamy czy maszyna już jest, żeby ją zaktualizować zamiast dublować
                            val index = sidebarItems.indexOfFirst { it.id == newItem.id }
                            if (index != -1) {
                                sidebarItems[index] = newItem
                            } else {
                                sidebarItems.add(newItem)
                            }
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }.start()
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.isConnected ?: false
    }

    private fun loadData() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        val FIELDS_KEY = "CACHED_FIELDS"
        val RAINS_KEY = "CACHED_RAINS"

        Thread {
            // --- POLA (Cache w tle) ---
            val cachedFields = CacheHelper.loadList<FieldItem>(this, FIELDS_KEY)
            runOnUiThread {
                if (cachedFields != null) {
                    updateFieldsUI(cachedFields)
                    centerMapOnFields(cachedFields)
                }
            }

            // --- MASZYNY (Cache w tle) ---
            val cachedRains = CacheHelper.loadList<Rain>(this, RAINS_KEY)
            runOnUiThread {
                if (cachedRains != null) {
                    updateRainsUI(cachedRains)
                }
            }

            // --- POBIERANIE Z SERWERA (Tło) ---
            remoteRepo.getAgriculturalFields(email) { fields ->
                if (fields.isNotEmpty()) {
                    CacheHelper.saveList(this, FIELDS_KEY, fields)
                    runOnUiThread {
                        updateFieldsUI(fields)
                        if (isOnline()) {
                            fields.forEach { downloadFieldTiles(it) }
                        }
                    }
                }
            }

            remoteRepo.getRains(email) { rains ->
                if (rains.isNotEmpty()) {
                    CacheHelper.saveList(this, RAINS_KEY, rains)
                    runOnUiThread { updateRainsUI(rains) }
                }
            }
        }.start()
    }

    // Pomocnicza funkcja do odświeżania widoku PÓL (żeby nie powtarzać kodu)
    private fun updateFieldsUI(fields: List<FieldItem>) {
        // Czyścimy stare poligony, ale zostawiamy markery i inne rzeczy
        map.overlays.removeAll { it is Polygon }
        fields.forEach { drawFieldOnMap(it) }
        setupFieldsSidebar(fields) // <--- TO NAPRAWIA PUSTY PASEK BOCZNY
        map.invalidate()
    }

    // Pomocnicza funkcja do odświeżania widoku MASZYN
    private fun updateRainsUI(rains: List<Rain>) {
        map.overlays.removeAll { it is Marker }
        rains.forEach { addRainMarker(it) }
        setupRainsSidebar(rains) // <--- TO NAPRAWIA PUSTY PASEK BOCZNY
        map.invalidate()
    }

    private fun downloadFieldTiles(field: FieldItem) {
        val firstCoord = field.coordinates.split(";")[0].split(",")
        if (firstCoord.size == 2) {
            val point = GeoPoint(firstCoord[0].toDouble(), firstCoord[1].toDouble())
            triggerCacheDownload(point, "Pole: ${field.name}")
        }
    }

    private fun downloadRainTiles(rain: Rain) {
        remoteRepo.getRainHistory(rain.id) { history ->
            if (history.isNotEmpty()) {
                val latest = history[0]
                val center = GeoPoint(latest.lat, latest.lng)
                runOnUiThread {
                    triggerCacheDownload(center, "Deszczownia: ${rain.name}")
                }
            }
        }
    }

    private fun triggerCacheDownload(center: GeoPoint, label: String) {
        val cacheManager = CacheManager(map)
        val delta = 0.005 // Zmniejszamy z 0.01 na 0.005 (węższy obszar wokół pola)
        val bbox = BoundingBox(
            center.latitude + delta, center.longitude + delta,
            center.latitude - delta, center.longitude - delta
        )

        // Pobieramy zoomy 14-17 (zamiast 13-17), żeby było mniej kafelków do pobrania na raz
        cacheManager.downloadAreaAsync(this, bbox, 14, 17, object : CacheManager.CacheManagerCallback {
            override fun downloadStarted() { Log.d("OFFLINE", "Start: $label") }
            override fun setPossibleTilesInArea(total: Int) {}
            override fun updateProgress(progress: Int, currentZoomLevel: Int, x: Int, y: Int) {}
            override fun onTaskComplete() { Log.d("OFFLINE", "Gotowe: $label") }
            override fun onTaskFailed(errors: Int) {}
        })
    }



    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}