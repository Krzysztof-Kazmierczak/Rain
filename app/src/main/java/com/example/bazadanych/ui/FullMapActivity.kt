package com.example.bazadanych.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import com.example.bazadanych.data.db.FieldEntity
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_full_map)

        setupToolbar()
        setupMap()
        initUI()
        loadData()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar) // WAŻNE: żeby menu działało
        drawerLayout = findViewById(R.id.mapDrawerLayout)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // Dodanie przycisku wyjścia w Toolbaurze
   /* override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // WAŻNE: upewnij się, że Twój plik menu nazywa się map_menu.xml. Jeśli to drawer_menu.xml, zostaw drawer_menu
        menuInflater.inflate(R.menu.drawer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // WAŻNE: Zmieniłem id na action_exit (takie daliśmy w pliku XML menu)
        if (item.itemId == R.id.nav_home) {
            finish() // Zamyka aktywność i wraca do menu
            return true
        }
        return super.onOptionsItemSelected(item)
    }*/

    private fun setupMap() {
        map = findViewById(R.id.fullMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (isDrawingMode && p != null) {
                    fieldPoints.add(p)
                    // Gdy stawiamy punkty, "COFNIJ" musi być widoczne
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

    private fun drawFieldOnMap(field: FieldEntity) {
        val pts = field.coordinates.split(";").mapNotNull {
            val latLng = it.split(",")
            if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
        }

        val poly = Polygon(map).apply {
            points = pts
            fillPaint.color = Color.parseColor(field.color)
            outlinePaint.color = Color.BLACK
            outlinePaint.strokeWidth = 2f
            title = field.name ?: "Pole"
            snippet = "🌾 Uprawa: ${field.cropType}\n📐 Powierzchnia: ${String.format("%.2f", field.areaHa)} ha\n\n(KLIKNIJ PONOWNIE W POLE, BY EDYTOWAĆ)"
            infoWindow = BasicInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, map)
        }

        poly.setOnClickListener { polygon, _, _ ->
            if (polygon.isInfoWindowOpen) {
                val intent = Intent(this@FullMapActivity, FieldEditActivity::class.java).apply {
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
                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(map)
                polygon.showInfoWindow()
            }
            true
        }
        map.overlays.add(poly)
    }

    private fun initUI() {
        btnStartDrawing = findViewById(R.id.btnStartDrawing)
        btnUndo = findViewById(R.id.btnUndo)

        btnStartDrawing.setOnClickListener { toggleDrawingMode() }
        btnUndo.setOnClickListener {
            if (fieldPoints.isNotEmpty()) {
                fieldPoints.removeAt(fieldPoints.size - 1)

                // DODAJEMY TEN WARUNEK:
                if (fieldPoints.isEmpty()) {
                    isDrawingMode = false           // Wyłączamy tryb rysowania
                    btnStartDrawing.text = "DODAJ POLE" // Wracamy do pierwotnego napisu
                    btnUndo.visibility = View.GONE  // Chowamy przycisk COFNIJ
                }

                updateDrawingLine()
            }
        }

        findViewById<FloatingActionButton>(R.id.btnCenterAll).setOnClickListener {
            loadData()
        }

        findViewById<Button>(R.id.menuRainsHeader).setOnClickListener {
            val rec = findViewById<RecyclerView>(R.id.recyclerRains)
            rec.visibility = if (rec.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.menuFieldsHeader).setOnClickListener {
            val rec = findViewById<RecyclerView>(R.id.recyclerFields)
            rec.visibility = if (rec.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<LinearLayout>(R.id.btnBackToMainMenu).setOnClickListener {
            finish() // Wychodzi do menu głównego
        }
    }

    private fun loadData() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        if (email.isEmpty()) return

        remoteRepo.getAgriculturalFields(email) { fields ->
            runOnUiThread {
                map.overlays.removeAll { it is Polygon }
                fields.forEach { drawFieldOnMap(it) }
                setupFieldsSidebar(fields)
                if (fields.isNotEmpty()) centerMapOnFields(fields)
                map.invalidate()
            }
        }

        // WAŻNE: Zmieniłem na getAllRains, bo tak nazwaliśmy tę funkcję w RainRemoteRepository
        remoteRepo.getRains(email) { rains ->
            runOnUiThread {
                map.overlays.removeAll { it is Marker }
                rains.forEach { addRainMarker(it) }
                setupRainsSidebar(rains)
                map.invalidate()
            }
        }
    }

    private fun centerMapOnFields(fields: List<FieldEntity>) {
        val allPoints = mutableListOf<GeoPoint>()
        fields.forEach { field ->
            field.coordinates.split(";").forEach {
                val latLng = it.split(",")
                if (latLng.size == 2) allPoints.add(GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()))
            }
        }

        if (allPoints.isNotEmpty()) {
            val boundingBox = BoundingBox.fromGeoPoints(allPoints)
            map.zoomToBoundingBox(boundingBox, true, 150)
        }
    }

    private fun addRainMarker(rain: Rain) {
        remoteRepo.getRainHistory(rain.id) { history ->
            if (history.isNotEmpty()) {
                val latest = history[0]
                runOnUiThread {
                    val marker = Marker(map).apply {
                        position = GeoPoint(latest.lat, latest.lng)
                        title = rain.name
                        snippet = "Kliknij, aby zarządzać"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { m, _ ->
                            if (m.isInfoWindowOpen) {
                                startActivity(Intent(this@FullMapActivity, RainDetailsActivity::class.java).putExtra("id", rain.id))
                            } else m.showInfoWindow()
                            true
                        }
                    }
                    map.overlays.add(marker)
                }
            }
        }
    }

    // POPRAWIONE: Logika dynamicznej zmiany przycisków!
    private fun toggleDrawingMode() {
        isDrawingMode = !isDrawingMode
        if (isDrawingMode) {
            fieldPoints.clear()
            updateDrawingLine()

            // Zmiana tekstu i pokazanie "Cofnij"
            btnStartDrawing.text = "ZAKOŃCZ POLE"
            btnUndo.visibility = View.VISIBLE
        } else {
            if (fieldPoints.size >= 3) {
                goToFieldEdit()
            } else {
                isDrawingMode = true // Wymuszamy pozostanie w trybie rysowania
                Toast.makeText(this, "Zaznacz min. 3 punkty!", Toast.LENGTH_SHORT).show()
            }
        }
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

    // POPRAWIONE: Przywrócenie pierwotnego stanu przycisków
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

        isDrawingMode = false
        btnStartDrawing.text = "DODAJ POLE"
        btnUndo.visibility = View.GONE // Chowamy przycisk cofnij
        fieldPoints.clear()
        updateDrawingLine()
    }

    private fun setupFieldsSidebar(fields: List<FieldEntity>) {
        val items = fields.map {
            val firstPt = it.coordinates.split(";")[0].split(",")
            MapSidebarAdapter.SidebarItem(it.id.toString(), it.name ?: "Pole", firstPt[0].toDouble(), firstPt[1].toDouble())
        }
        val recycler = findViewById<RecyclerView>(R.id.recyclerFields)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = MapSidebarAdapter(items) { item ->
            map.controller.animateTo(GeoPoint(item.lat, item.lng))
            map.controller.setZoom(17.0)
            drawerLayout.closeDrawers()
        }
    }

    private fun setupRainsSidebar(rains: List<Rain>) {
        val recycler = findViewById<RecyclerView>(R.id.recyclerRains)
        recycler.layoutManager = LinearLayoutManager(this)
        val sidebarItems = mutableListOf<MapSidebarAdapter.SidebarItem>()
        rains.forEach { rain ->
            remoteRepo.getRainHistory(rain.id) { history ->
                if (history.isNotEmpty()) {
                    sidebarItems.add(MapSidebarAdapter.SidebarItem(rain.id, rain.name, history[0].lat, history[0].lng))
                    runOnUiThread {
                        recycler.adapter = MapSidebarAdapter(sidebarItems) { item ->
                            map.controller.animateTo(GeoPoint(item.lat, item.lng))
                            map.controller.setZoom(18.0)
                            drawerLayout.closeDrawers()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        loadData()
    }
}