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
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent

class FullMapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private val remoteRepo = RainRemoteRepository()
    private var isDrawingMode = false
    private var isPlacingRainMode = false
    private var rainIdToPlace: String? = null
    private var drawingPolyline = Polyline()
    private val fieldPoints = mutableListOf<GeoPoint>()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnStartDrawing: Button
    private lateinit var btnUndo: Button

    private val originalRainBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.color_deszczowniav2)
    }

    private val scaledRainDrawablesCache = mutableMapOf<Int, Drawable>()

    private val MAP_CONFIG_KEY = "LAST_MAP_CONFIG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val conf = org.osmdroid.config.Configuration.getInstance()
        val ctx = applicationContext

        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        conf.userAgentValue = packageName
        conf.tileFileSystemCacheMaxBytes = 1000L * 1024L * 1024L

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

        val lastConfig = CacheHelper.loadObject<CacheHelper.MapConfig>(this, MAP_CONFIG_KEY)
        if (lastConfig != null) {
            map.controller.setZoom(lastConfig.zoom)
            map.controller.setCenter(GeoPoint(lastConfig.lat, lastConfig.lng))
        } else {
            map.controller.setZoom(6.0)
            map.controller.setCenter(GeoPoint(52.0, 19.0))
        }

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false

            override fun onZoom(event: ZoomEvent?): Boolean {
                val currentZoom = map.zoomLevelDouble
                val newIcon = getOrCreateScaledDrawable(currentZoom)

                map.overlays.forEach { overlay ->
                    if (overlay is Marker && overlay.id?.startsWith("rain_") == true) {
                        overlay.icon = newIcon
                    }
                }
                map.invalidate()
                return true
            }
        })

        loadData()
    }

    private fun getOrCreateScaledDrawable(zoom: Double): Drawable {
        val intZoom = zoom.toInt()

        return scaledRainDrawablesCache.getOrPut(intZoom) {
            val baseZoom = 15
            val zoomDiff = intZoom - baseZoom
            var scaleFactor = Math.pow(2.0, zoomDiff.toDouble())

            scaleFactor = scaleFactor.coerceIn(0.8, 3.0)

            val baseWidthPx = 120
            val aspectRatio = originalRainBitmap.height.toFloat() / originalRainBitmap.width.toFloat()

            var targetWidth = (baseWidthPx * scaleFactor).toInt()
            var targetHeight = (targetWidth * aspectRatio).toInt()

            if (targetWidth <= 0) targetWidth = 1
            if (targetHeight <= 0) targetHeight = 1

            val scaledBitmap = Bitmap.createScaledBitmap(originalRainBitmap, targetWidth, targetHeight, true)
            BitmapDrawable(resources, scaledBitmap)
        }
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

                if (isPlacingRainMode && p != null && rainIdToPlace != null) {
                    saveRainManualLocation(rainIdToPlace!!, p)
                    return true
                }

                org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(map)
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        map.overlays.add(eventsOverlay)
    }

    private fun saveRainManualLocation(rainId: String, p: GeoPoint) {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        Log.d("FullMapDebug", "Próba zapisu dla RainID: $rainId, Email: $email na koordynatach: ${p.latitude}, ${p.longitude}")

        remoteRepo.updateRainManualLocation(rainId, email, p.latitude, p.longitude) { success ->
            runOnUiThread {
                if (success) {
                    Log.d("FullMapDebug", "Sukces! Lokalizacja zapisana w bazie.")
                    Toast.makeText(this, getString(R.string.full_map_location_set), Toast.LENGTH_SHORT).show()
                    isPlacingRainMode = false
                    rainIdToPlace = null
                    loadData()
                } else {
                    Log.e("FullMapDebug", "Porażka! Sprawdź Logcat dla RainRepoDebug.")
                    Toast.makeText(this, getString(R.string.full_map_save_error_log), Toast.LENGTH_LONG).show()
                    isPlacingRainMode = false
                    rainIdToPlace = null
                }
            }
        }
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

    private fun drawFieldOnMap(field: FieldItem) {
        val pts = field.coordinates.split(";").mapNotNull {
            val latLng = it.split(",")
            if (latLng.size == 2) {
                val lat = latLng[0].toDoubleOrNull() ?: 0.0
                val lng = latLng[1].toDoubleOrNull() ?: 0.0
                GeoPoint(lat, lng)
            } else null
        }

        val poly = Polygon(map).apply {
            points = pts
            fillPaint.color = Color.parseColor(field.color)
            fillPaint.alpha = 100
            outlinePaint.color = Color.BLACK
            outlinePaint.strokeWidth = 2f

            // Tłumaczenie tytułu i wstrzyknięcie zmiennych do zasobu HTML (snippet)
            title = field.name ?: getString(R.string.full_map_default_field_name)
            snippet = getString(R.string.full_map_field_snippet, field.cropType, field.areaHa)

            infoWindow = BasicInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, map)
        }

        poly.setOnClickListener { polygon, _, _ ->
            if (isPlacingRainMode || isDrawingMode) {
                return@setOnClickListener false
            }

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
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        Thread {
            val cachedHistory = CacheHelper.loadList<com.example.bazadanych.data.db.RainStatus>(this, HISTORY_KEY)

            if (cachedHistory != null && cachedHistory.isNotEmpty()) {
                runOnUiThread {
                    drawRainMarkerOnMap(rain, cachedHistory[0].lat, cachedHistory[0].lng)
                }
            }

            remoteRepo.getRainHistory(rain.id, email) { history ->
                CacheHelper.saveList(this, HISTORY_KEY, history)

                runOnUiThread {
                    if (history.isNotEmpty()) {
                        drawRainMarkerOnMap(rain, history[0].lat, history[0].lng)
                    } else {
                        map.overlays.removeAll { it is Marker && it.title == rain.name }
                        map.invalidate()
                    }
                }
            }
        }.start()
    }

    private fun drawRainMarkerOnMap(rain: Rain, lat: Double, lng: Double) {
        map.overlays.removeAll { it is Marker && it.id == "rain_${rain.id}" }

        if (lat == 0.0 && lng == 0.0) {
            map.invalidate()
            return
        }

        val marker = Marker(map).apply {
            id = "rain_${rain.id}"
            position = GeoPoint(lat, lng)
            title = rain.name

            icon = getOrCreateScaledDrawable(map.zoomLevelDouble)
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
            btnStartDrawing.text = getString(R.string.full_map_btn_finish_field)
            btnUndo.visibility = View.VISIBLE
        } else {
            if (fieldPoints.size >= 3) {
                goToFieldEdit()
            } else {
                Toast.makeText(this, getString(R.string.full_map_min_points_toast), Toast.LENGTH_SHORT).show()
                isDrawingMode = true
            }
        }
    }

    private fun resetDrawingState() {
        isDrawingMode = false
        btnStartDrawing.text = getString(R.string.full_map_btn_add_field)
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
                    val centerLat = allPoints.map { it.latitude }.average()
                    val centerLng = allPoints.map { it.longitude }.average()

                    MapSidebarAdapter.SidebarItem(
                        field.id.toString(),
                        field.name ?: getString(R.string.full_map_default_field_name),
                        centerLat,
                        centerLng
                    )
                } else null
            } else null
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerFields)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = MapSidebarAdapter(items) { item ->
            val field = fields.find { it.id.toString() == item.id }

            if (field != null) {
                val pts = field.coordinates.split(";").mapNotNull {
                    val latLng = it.split(",")
                    if (latLng.size == 2) GeoPoint(latLng[0].toDouble(), latLng[1].toDouble()) else null
                }

                if (pts.isNotEmpty()) {
                    val box = BoundingBox.fromGeoPoints(pts)
                    map.zoomToBoundingBox(box, true, 150)
                }
            }
            drawerLayout.closeDrawers()
        }
    }

    private fun setupRainsSidebar(rains: List<Rain>) {
        val recycler = findViewById<RecyclerView>(R.id.recyclerRains)
        recycler.layoutManager = LinearLayoutManager(this)

        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        val sidebarItems = mutableListOf<MapSidebarAdapter.SidebarItem>()
        val adapter = MapSidebarAdapter(sidebarItems) { item ->
            if (item.lat == 0.0 && item.lng == 0.0) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.full_map_no_gps_title))
                    .setMessage(getString(R.string.full_map_no_gps_message))
                    .setPositiveButton(getString(R.string.full_map_btn_yes)) { _, _ ->
                        isPlacingRainMode = true
                        rainIdToPlace = item.id
                        Toast.makeText(this, getString(R.string.full_map_click_to_place), Toast.LENGTH_LONG).show()
                        drawerLayout.closeDrawers()
                    }
                    .setNegativeButton(getString(R.string.full_map_btn_no), null)
                    .show()
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
                val cachedHistory = CacheHelper.loadList<com.example.bazadanych.data.db.RainStatus>(this, HISTORY_KEY)
                if (cachedHistory != null && cachedHistory.isNotEmpty()) {
                    val cachedItem = MapSidebarAdapter.SidebarItem(
                        rain.id,
                        rain.name,
                        cachedHistory[0].lat,
                        cachedHistory[0].lng
                    )

                    runOnUiThread {
                        if (!sidebarItems.any { it.id == cachedItem.id }) {
                            sidebarItems.add(cachedItem)
                            adapter.notifyDataSetChanged()
                        }
                    }
                }

                remoteRepo.getRainHistory(rain.id, email) { history ->
                    if (history.isNotEmpty()) {
                        val newItem = MapSidebarAdapter.SidebarItem(
                            rain.id,
                            rain.name,
                            history[0].lat,
                            history[0].lng
                        )

                        runOnUiThread {
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
            val cachedFields = CacheHelper.loadList<FieldItem>(this, FIELDS_KEY)
            runOnUiThread {
                if (cachedFields != null) {
                    updateFieldsUI(cachedFields)
                    centerMapOnFields(cachedFields)
                }
            }

            val cachedRains = CacheHelper.loadList<Rain>(this, RAINS_KEY)
            runOnUiThread {
                if (cachedRains != null) {
                    updateRainsUI(cachedRains)
                }
            }

            remoteRepo.getAgriculturalFields(email) { fields ->
                CacheHelper.saveList(this, FIELDS_KEY, fields)

                runOnUiThread {
                    updateFieldsUI(fields)
                    if (isOnline() && fields.isNotEmpty()) {
                        fields.forEach { downloadFieldTiles(it) }
                    }
                }
            }

            remoteRepo.getRains(email) { rains ->
                CacheHelper.saveList(this, RAINS_KEY, rains)

                runOnUiThread {
                    updateRainsUI(rains)
                }
            }
        }.start()
    }

    private fun updateFieldsUI(fields: List<FieldItem>) {
        map.overlays.removeAll { it is Polygon }
        fields.forEach { drawFieldOnMap(it) }
        setupFieldsSidebar(fields)
        map.invalidate()
    }

    private fun updateRainsUI(rains: List<Rain>) {
        map.overlays.removeAll { it is Marker }
        rains.forEach { addRainMarker(it) }
        setupRainsSidebar(rains)
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
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.getRainHistory(rain.id, email) { history ->
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
        val delta = 0.005
        val bbox = BoundingBox(
            center.latitude + delta, center.longitude + delta,
            center.latitude - delta, center.longitude - delta
        )

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
        loadData()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}