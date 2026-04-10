package com.example.bazadanych.ui

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bazadanych.R
import com.example.bazadanych.data.api.ApiClient
import com.example.bazadanych.data.db.DataBase
import com.example.bazadanych.data.db.FieldDao // ZMIENIONE Z RainDao na FieldDao!
import com.example.bazadanych.data.db.FieldHistory
import com.example.bazadanych.data.db.toDomainModel
import com.example.bazadanych.data.db.toEntity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var chartWeather: LineChart
    private lateinit var chartMachine: LineChart
    private var fullHistoryData: List<FieldHistory> = emptyList()
    private var fieldId: Int = 1
    private var fieldName: String = ""

    // Aktywne checkboxy (max 2 per wykres)
    private val activeWeatherParams = mutableListOf(R.id.cbTemp, R.id.cbRain)
    private val activeMachineParams = mutableListOf(R.id.cbSpeed)
    private var currentInterval: Int = 1 // Domyślnie surowe dane z API to co 3h
    private var isForecastVisible: Boolean = true // Domyślnie prognoza jest włączona

    // Inicjalizacja bazy
    private lateinit var database: DataBase
    private lateinit var fieldDao: FieldDao // TUTAJ BYŁ BŁĄD (było RainDao)

    private var displayData: List<FieldHistory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        // 1. Inicjalizacja bazy
        database = DataBase.getDatabase(this)
        fieldDao = database.fieldDao()

        fieldId = intent.getIntExtra("FIELD_ID", 1)
        fieldName = intent.getStringExtra("FIELD_NAME") ?: "Pole"

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarAnalytics)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Historia i Prognoza: $fieldName"
        toolbar.setNavigationOnClickListener { finish() }

        chartWeather = findViewById(R.id.chartWeather)
        chartMachine = findViewById(R.id.chartMachine)

        setupChartStyle(chartWeather, isTopChart = true)
        setupChartStyle(chartMachine, isTopChart = false)

        syncCharts(chartWeather, chartMachine)
        syncCharts(chartMachine, chartWeather)

        setupCheckboxes()

        // USUNIĘTO STARE loadDataFromServer(fieldId) Z TEGO MIEJSCA

        // Obsługa przycisku oka (włącz/wyłącz prognozę)
        val btnToggleForecast = findViewById<ImageButton>(R.id.btnToggleForecast)
        btnToggleForecast.setOnClickListener {
            isForecastVisible = !isForecastVisible
            val iconRes = if (isForecastVisible) R.drawable.ic_visibility else R.drawable.ic_visibility
            btnToggleForecast.setImageResource(iconRes)
            val msg = if (isForecastVisible) "Prognoza włączona" else "Prognoza ukryta"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            updateChartsData()
        }

        // Zmodyfikowany nasłuchiwacz Chipów
        findViewById<ChipGroup>(R.id.chipGroupInterval).setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val interval = when(checkedId) {
                R.id.chip1h -> 1
                R.id.chip12h -> 12
                R.id.chipDay -> 24
                else -> 3
            }
            if (interval == 1) {
                android.widget.Toast.makeText(this, "Skala 1h pokazuje tylko historię maszyny. Prognoza jest co 3h.", android.widget.Toast.LENGTH_LONG).show()
            }
            currentInterval = interval
            applyAggregation(interval)
        }

        // 2. Najpierw ładujemy to co mamy w bazie (Offline First)
        loadDataFromRoom(fieldId)

        // 3. Potem odświeżamy z serwera
        refreshDataFromServer(fieldId)
    }

    private fun setupCheckboxes() {
        val weatherCbs = listOf(R.id.cbTemp, R.id.cbRain, R.id.cbClouds)
        val machineCbs = listOf(R.id.cbSpeed, R.id.cbWind, R.id.cbHumidity)

        weatherCbs.forEach { id ->
            findViewById<CheckBox>(id).setOnCheckedChangeListener { cb, isChecked ->
                handleCheckboxLimit(cb, isChecked, activeWeatherParams)
            }
        }
        machineCbs.forEach { id ->
            findViewById<CheckBox>(id).setOnCheckedChangeListener { cb, isChecked ->
                handleCheckboxLimit(cb, isChecked, activeMachineParams)
            }
        }
    }

    private fun handleCheckboxLimit(cb: android.widget.CompoundButton, isChecked: Boolean, activeList: MutableList<Int>) {
        if (isChecked) {
            if (activeList.size >= 2 && !activeList.contains(cb.id)) {
                val oldestId = activeList.removeAt(0)
                findViewById<CheckBox>(oldestId).isChecked = false
            }
            if (!activeList.contains(cb.id)) activeList.add(cb.id)
        } else {
            activeList.remove(cb.id)
        }
        updateChartsData()
    }

    // --- NOWE ZARZĄDZANIE DANYMI (ROOM) ---

    private fun loadDataFromRoom(fieldId: Int) {
        lifecycleScope.launch {
            val cachedEntities = withContext(Dispatchers.IO) {
                fieldDao.getHistoryForField(fieldId)
            }
            if (cachedEntities.isNotEmpty()) {
                val historyList = cachedEntities.map { it.toDomainModel() }
                processAndDisplay(historyList)
            }
        }
    }

    private fun refreshDataFromServer(fieldId: Int) {
        ApiClient.rainTech.getFieldHistory(fieldId).enqueue(object : Callback<List<FieldHistory>> {
            override fun onResponse(call: Call<List<FieldHistory>>, response: Response<List<FieldHistory>>) {
                if (response.isSuccessful && response.body() != null) {
                    val rawData = response.body()!!
                    lifecycleScope.launch(Dispatchers.IO) {
                        val entities = rawData.map { it.toEntity(fieldId) }
                        fieldDao.deleteAndInsert(fieldId, entities)

                        withContext(Dispatchers.Main) {
                            processAndDisplay(rawData)
                        }
                    }
                }
            }

            override fun onFailure(call: Call<List<FieldHistory>>, t: Throwable) {
                android.widget.Toast.makeText(this@AnalyticsActivity, "Brak połączenia. Wyświetlam dane z pamięci.", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- LOGIKA WIDOKÓW I WYKRESÓW ---

    private fun processAndDisplay(rawData: List<FieldHistory>) {
        val lastHistoryPoint = rawData.lastOrNull { it.is_forecast == 0 }
        fullHistoryData = if (lastHistoryPoint != null && lastHistoryPoint.recorded_at != null) {
            rawData.filter {
                it.is_forecast == 0 || (it.is_forecast == 1 && it.recorded_at!! > lastHistoryPoint.recorded_at!!)
            }
        } else {
            rawData
        }
        applyAggregation(currentInterval)
    }

    private fun applyAggregation(hours: Int) {
        if (hours == 1) {
            displayData = fullHistoryData.filter { it.is_forecast != 1 }
        } else if (hours == 0 || hours == 3) {
            displayData = fullHistoryData
        } else {
            val step = (hours / 3).coerceAtLeast(1)
            displayData = fullHistoryData.windowed(size = step, step = step, partialWindows = true).map { window ->
                FieldHistory(
                    id = window[0].id,
                    field_id = window[0].field_id,
                    temperature = window.mapNotNull { it.temperature }.average(),
                    rain_mm = window.mapNotNull { it.rain_mm }.sum(),
                    humidity = window.mapNotNull { it.humidity }.average().toInt(),
                    wind_speed = window.mapNotNull { it.wind_speed }.average(),
                    is_forecast = window.last().is_forecast,
                    recorded_at = window.last().recorded_at,
                    machine_speed = window.mapNotNull { it.machine_speed }.average(),
                    wind_deg = window[0].wind_deg,
                    pressure = window[0].pressure,
                    clouds = window[0].clouds,
                    created_at = window[0].created_at
                )
            }
        }
        updateChartsData()
    }

    private fun setupChartStyle(chart: LineChart, isTopChart: Boolean) {
        chart.apply {
            description.isEnabled = false
            setNoDataText("Brak danych...")
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            axisRight.isEnabled = true

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                chartWeather.setVisibleXRangeMaximum(24f)
                chartMachine.setVisibleXRangeMaximum(24f)
                isGranularityEnabled = true
                setDrawLabels(true)
                labelRotationAngle = -45f

                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val firstTimeStr = displayData.firstOrNull()?.recorded_at ?: return ""
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val calendar = java.util.Calendar.getInstance()

                        calendar.time = sdf.parse(firstTimeStr) ?: return ""
                        calendar.add(java.util.Calendar.HOUR_OF_DAY, value.toInt())

                        val resultDate = calendar.time
                        val outSdf = if (currentInterval >= 24) {
                            java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault())
                        } else {
                            java.text.SimpleDateFormat("HH:00", java.util.Locale.getDefault())
                        }
                        return outSdf.format(resultDate)
                    }
                }
            }
            setVisibleXRangeMaximum(10f)
            moveViewToX(displayData.size.toFloat())
        }
    }

    private fun updateChartsData() {
        chartWeather.highlightValues(null)
        chartMachine.highlightValues(null)

        if (fullHistoryData.isEmpty()) {
            chartWeather.clear()
            chartMachine.clear()
            return
        }

        val weatherData = LineData()
        val tvWeatherLeft = findViewById<TextView>(R.id.tvWeatherLeftLabel)
        val tvWeatherRight = findViewById<TextView>(R.id.tvWeatherRightLabel)

        tvWeatherLeft.text = ""
        tvWeatherRight.text = ""
        chartWeather.axisLeft.isEnabled = false
        chartWeather.axisRight.isEnabled = false

        activeWeatherParams.forEachIndexed { index, id ->
            val (label, color, extractor) = getParamInfo(id)
            val axis = if (index == 0) YAxis.AxisDependency.LEFT else YAxis.AxisDependency.RIGHT
            addLineWithForecast(weatherData, label, color, axis, extractor)

            if (index == 0) {
                tvWeatherLeft.text = "← $label"
                chartWeather.axisLeft.isEnabled = true
                chartWeather.axisLeft.textColor = color
            } else {
                tvWeatherRight.text = "$label →"
                chartWeather.axisRight.isEnabled = true
                chartWeather.axisRight.textColor = color
            }
        }

        val machineData = LineData()
        val tvMachineLeft = findViewById<TextView>(R.id.tvMachineLeftLabel)
        val tvMachineRight = findViewById<TextView>(R.id.tvMachineRightLabel)

        tvMachineLeft.text = ""
        tvMachineRight.text = ""
        chartMachine.axisLeft.isEnabled = false
        chartMachine.axisRight.isEnabled = false

        activeMachineParams.forEachIndexed { index, id ->
            val (label, color, extractor) = getParamInfo(id)
            val axis = if (index == 0) YAxis.AxisDependency.LEFT else YAxis.AxisDependency.RIGHT
            addLineWithForecast(machineData, label, color, axis, extractor)

            if (index == 0) {
                tvMachineLeft.text = "← $label"
                chartMachine.axisLeft.isEnabled = true
                chartMachine.axisLeft.textColor = color
            } else {
                tvMachineRight.text = "$label →"
                chartMachine.axisRight.isEnabled = true
                chartMachine.axisRight.textColor = color
            }
        }

        if (weatherData.dataSetCount > 0) chartWeather.data = weatherData else chartWeather.clear()
        if (machineData.dataSetCount > 0) chartMachine.data = machineData else chartMachine.clear()

        val markerView = CustomMarkerView(this, R.layout.view_marker)
        chartWeather.marker = markerView
        chartMachine.marker = markerView

        chartWeather.invalidate()
        chartMachine.invalidate()
    }

    private fun addLineWithForecast(
        lineData: LineData,
        label: String,
        colorCode: Int,
        axis: YAxis.AxisDependency,
        valueExtractor: (FieldHistory) -> Float?
    ) {
        val pastEntries = mutableListOf<Entry>()
        val futureEntries = mutableListOf<Entry>()

        val firstTimeStr = displayData.firstOrNull()?.recorded_at ?: return
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val firstDate = sdf.parse(firstTimeStr) ?: return
        val firstTimestamp = firstDate.time

        displayData.forEach { d ->
            val value = valueExtractor(d)
            if (value != null) {
                val currentDate = sdf.parse(d.recorded_at ?: "") ?: return@forEach
                val hoursOffset = (currentDate.time - firstTimestamp).toFloat() / (1000 * 60 * 60)
                if (d.is_forecast != 1) {
                    pastEntries.add(Entry(hoursOffset, value, d))
                } else {
                    futureEntries.add(Entry(hoursOffset, value, d))
                }
            }
        }

        if (isForecastVisible && pastEntries.isNotEmpty() && futureEntries.isNotEmpty()) {
            futureEntries.add(0, pastEntries.last())
        }
        if (pastEntries.isNotEmpty()) {
            lineData.addDataSet(createDataSet(pastEntries, label, colorCode, axis, isDashed = false))
        }
        if (isForecastVisible && futureEntries.size > 1) {
            lineData.addDataSet(createDataSet(futureEntries, "$label (Prognoza)", colorCode, axis, isDashed = true))
        }
    }

    private fun createDataSet(entries: List<Entry>, label: String, colorCode: Int, axis: YAxis.AxisDependency, isDashed: Boolean): LineDataSet {
        return LineDataSet(entries, label).apply {
            axisDependency = axis
            color = colorCode
            setCircleColor(colorCode)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawHighlightIndicators(true)
            setDrawHorizontalHighlightIndicator(true)
            setDrawVerticalHighlightIndicator(true)
            highlightLineWidth = 2f
            highLightColor = colorCode
            enableDashedHighlightLine(10f, 5f, 0f)

            if (label.contains("Opady")) {
                setDrawFilled(true)
                fillColor = Color.BLUE
                fillAlpha = 50
            }
            if (isDashed) {
                enableDashedLine(10f, 10f, 0f)
                setDrawCircles(false)
                form = com.github.mikephil.charting.components.Legend.LegendForm.NONE
            }
        }
    }

    private fun syncCharts(mainChart: LineChart, secondaryChart: LineChart) {
        mainChart.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                secondaryChart.viewPortHandler.refresh(mainChart.viewPortHandler.matrixTouch, secondaryChart, true)
            }
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
                secondaryChart.viewPortHandler.refresh(mainChart.viewPortHandler.matrixTouch, secondaryChart, true)
            }
            override fun onChartGestureStart(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartGestureEnd(me: MotionEvent?, lastGesture: ChartTouchListener.ChartGesture?) {}
            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {}
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
        }
    }

    private fun getParamInfo(id: Int): Triple<String, Int, (FieldHistory) -> Float?> {
        return when (id) {
            R.id.cbTemp -> Triple("Temperatura (°C)", Color.RED) { it.temperature?.toFloat() }
            R.id.cbRain -> Triple("Opady (mm)", Color.BLUE) { it.rain_mm?.toFloat() }
            R.id.cbClouds -> Triple("Chmury (%)", Color.DKGRAY) { it.clouds?.toFloat() }
            R.id.cbSpeed -> Triple("Prędkość (km/h)", Color.parseColor("#4CAF50")) { if (it.is_forecast == 1) null else it.machine_speed?.toFloat() }
            R.id.cbWind -> Triple("Wiatr (m/s)", Color.parseColor("#FF9800")) { it.wind_speed?.toFloat() }
            R.id.cbHumidity -> Triple("Wilgotność (%)", Color.parseColor("#00BCD4")) { it.humidity?.toFloat() }
            else -> Triple("Błąd", Color.BLACK) { 0f }
        }
    }
}