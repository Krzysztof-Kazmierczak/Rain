package com.example.bazadanych.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.bazadanych.R
import com.example.bazadanych.data.api.ApiClient
import com.example.bazadanych.data.db.DataBase
import com.example.bazadanych.data.db.FieldDao // ZMIENIONE Z RainDao na FieldDao!
import com.example.bazadanych.data.db.FieldHistory
import com.example.bazadanych.data.db.toDomainModel
import com.example.bazadanych.data.db.toEntity
import com.example.bazadanych.data.model.MultilineXAxisRenderer
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
    private var currentInterval: Int = 3 // Domyślnie surowe dane z API to co 3h
    private var isForecastVisible: Boolean = true // Domyślnie prognoza jest włączona
    private lateinit var btnToggleForecast: com.google.android.material.button.MaterialButton
    private lateinit var btnClearDate: ImageButton

    // Inicjalizacja bazy
    private lateinit var database: DataBase
    private lateinit var fieldDao: FieldDao // TUTAJ BYŁ BŁĄD (było RainDao)

    private var displayData: List<FieldHistory> = emptyList()
    // NOWE ZMIENNE DO FILTROWANIA
    private var cachedRawData: List<FieldHistory> = emptyList()
    private var startDateFilter: Long? = null
    private var endDateFilter: Long? = null
    val visibleRange = 40f // Twój limit punktów

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        // 1. Inicjalizacja bazy i danych podstawowych
        database = DataBase.getDatabase(this)
        fieldDao = database.fieldDao()
        fieldId = intent.getIntExtra("FIELD_ID", 1)
        fieldName = intent.getStringExtra("FIELD_NAME") ?: "Pole"

        // Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarAnalytics)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Historia i Prognoza: $fieldName"
        toolbar.setNavigationOnClickListener { finish() }

        // Wykresy
        chartWeather = findViewById(R.id.chartWeather)
        chartMachine = findViewById(R.id.chartMachine)
        chartWeather.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        chartMachine.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        setupChartStyle(chartWeather, isTopChart = true)
        setupChartStyle(chartMachine, isTopChart = false)
        syncCharts(chartWeather, chartMachine)
        syncCharts(chartMachine, chartWeather)

        setupCheckboxes()

        // --- INICJALIZACJA PRZYCISKÓW ---
        btnToggleForecast = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleForecast)
        btnClearDate = findViewById<ImageButton>(R.id.btnClearDate)
        val btnDateRange = findViewById<ImageButton>(R.id.btnDateRange)

        // Obsługa przycisku prognozy (NOWA LOGIKA TEKSTOWA)
        btnToggleForecast.setOnClickListener {
            isForecastVisible = !isForecastVisible
            updateForecastButtonState() // Ta funkcja zmieni tekst na przycisku
            updateChartsData()
        }

        // Wybór zakresu dat
        btnDateRange.setOnClickListener {
            val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Wybierz zakres dat")
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                startDateFilter = selection.first
                endDateFilter = selection.second + 86399999L

                btnClearDate.visibility = android.view.View.VISIBLE
                updateForecastButtonState()
                processAndDisplay(cachedRawData)
            }
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        // Przycisk "X" do kasowania daty
        btnClearDate.setOnClickListener {
            startDateFilter = null
            endDateFilter = null
            btnClearDate.visibility = android.view.View.GONE
            updateForecastButtonState()
            processAndDisplay(cachedRawData)
        }

        // Obsługa Chipów (interwały)
        findViewById<ChipGroup>(R.id.chipGroupInterval).setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val interval = when(checkedId) {
                R.id.chip1h -> 1
                R.id.chip12h -> 12
                R.id.chipDay -> 24
                else -> 3
            }
            currentInterval = interval
            updateForecastButtonState() // Ważne: aktualizuje tekst przycisku przy zmianie na 1h
            applyAggregation(interval)
        }

        // 2. Ładowanie danych
        loadDataFromRoom(fieldId)
        refreshDataFromServer(fieldId)
        updateForecastButtonState()
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
        cachedRawData = rawData

        // 1. ZNAJDUJEMY OSTATNI HISTORYCZNY PUNKT W CAŁEJ BAZIE (Przed nałożeniem kalendarza)
        val absoluteLastHistoryPoint = cachedRawData.lastOrNull { it.is_forecast == 0 }

        // 2. USUŃ STARE PROGNOZY Z PRZESZŁOŚCI
        // Zostawiamy tylko historię (0) ORAZ prognozę (1), ale tylko tę nowszą od ostatniego znanego pomiaru
        var cleanData = cachedRawData.filter {
            it.is_forecast == 0 || (it.is_forecast == 1 && absoluteLastHistoryPoint != null && it.recorded_at!! > absoluteLastHistoryPoint.recorded_at!!)
        }

        // 3. FILTRUJEMY PO DACIE Z KALENDARZA
        if (startDateFilter != null && endDateFilter != null) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            cleanData = cleanData.filter {
                val dateStr = it.recorded_at
                if (dateStr != null) {
                    val time = sdf.parse(dateStr)?.time ?: 0L
                    time in startDateFilter!!..endDateFilter!!
                } else false
            }
        }

        fullHistoryData = cleanData
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

            // 1. USTAW NOWY RENDERER DLA WYKRESU (dodaj te dwie linijki przed blokiem xAxis):
            chart.extraBottomOffset = 15f // Robimy miejsce pod wykresem, żeby druga linia tekstu nie została ucięta
            chart.setXAxisRenderer(
                MultilineXAxisRenderer(
                    chart.viewPortHandler,
                    chart.xAxis,
                    chart.getTransformer(YAxis.AxisDependency.LEFT)
                )
            )

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f

                // 2. USTAW KĄT NA ZERO (żeby tekst był w poziomie)
                labelRotationAngle = 0f // Zamiast -45f

                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val firstTimeStr = displayData.firstOrNull()?.recorded_at ?: return ""
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val calendar = java.util.Calendar.getInstance()

                        calendar.time = sdf.parse(firstTimeStr) ?: return ""
                        calendar.add(java.util.Calendar.HOUR_OF_DAY, value.toInt())

                        val resultDate = calendar.time

                        // 3. DODAJ ZNAK NOWEJ LINII (\n) DO FORMATU
                        val outSdf = if (currentInterval >= 24) {
                            java.text.SimpleDateFormat("dd.MM\nyyyy", java.util.Locale.getDefault()) // np. 15.04 (enter) 2024
                        } else {
                            java.text.SimpleDateFormat("HH:00\ndd.MM", java.util.Locale.getDefault()) // np. 14:00 (enter) 15.04
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

        val charts = listOf(chartWeather, chartMachine)
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        // 1. Obliczamy pozycję X dla "teraz"
        var nowX = displayData.size.toFloat()
        val firstTimeStr = displayData.firstOrNull()?.recorded_at

        if (firstTimeStr != null) {
            val firstDate = sdf.parse(firstTimeStr)
            if (firstDate != null) {
                val firstTimestamp = firstDate.time
                nowX = (now - firstTimestamp).toFloat() / (1000 * 60 * 60)
            }
        }



        charts.forEach { chart ->
            if (startDateFilter == null && endDateFilter == null) {
                chart.setVisibleXRangeMaximum(visibleRange)

                // 2. POPRAWKA: Przesuwamy widok tak, aby nowX był na środku
                // Odejmujemy połowę widocznego zakresu (20 pkt), żeby punkt trafił w centrum
                val centerX = nowX - (visibleRange / 2f)
                chart.moveViewToX(centerX)
            } else {
                chart.setVisibleXRangeMaximum(Float.MAX_VALUE)
                chart.moveViewToX(0f)
            }
        }

        val markerView = CustomMarkerView(this, R.layout.view_marker)
        chartWeather.marker = markerView
        chartMachine.marker = markerView

        chartWeather.invalidate()
        chartMachine.invalidate()
        // Dodaj to tutaj:
        centerChartOnNow()
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
            // HISTORY: Not dashed
            lineData.addDataSet(createDataSet(pastEntries, label, colorCode, axis, isDashed = false))

            if (isForecastVisible && futureEntries.size > 1) {
                // FORECAST: Dashed. Pass the label so it functions correctly, but we'll hide it from the legend inside createDataSet
                lineData.addDataSet(createDataSet(futureEntries, "", colorCode, axis, isDashed = true))
            }
        }else
        {
            if (isForecastVisible && futureEntries.size > 1) {
                // FORECAST: Dashed. Pass the label so it functions correctly, but we'll hide it from the legend inside createDataSet
                lineData.addDataSet(createDataSet(futureEntries, label, colorCode, axis, isDashed = false))
            }
        }
    }

    private fun createDataSet(entries: List<Entry>, label: String, colorCode: Int, axis: YAxis.AxisDependency, isDashed: Boolean): LineDataSet {
        // DODAJ LOGA, żeby sprawdzić w Logcat czy w ogóle tworzy się zestaw przerywany
        if (isDashed) {
            android.util.Log.d("ChartDebug", "Tworzę przerywaną linię dla: $label, ilość punktów: ${entries.size}")
        }

        return LineDataSet(entries, label).apply {
            axisDependency = axis
            color = colorCode
            setCircleColor(colorCode)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)

            // Zmień CUBIC_BEZIER na LINEAR lub HORIZONTAL_BEZIER dla testu
            mode = if (isDashed) LineDataSet.Mode.LINEAR else LineDataSet.Mode.HORIZONTAL_BEZIER

            setDrawHighlightIndicators(true)

            if (isDashed) {
                // Spróbuj zwiększyć odstępy (np. 15f, 15f)
                enableDashedLine(15f, 15f, 0f)
                setDrawCircles(false)

                // To ukrywa element z legendy
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

    private fun centerChartOnNow() {
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        var nowX = displayData.size.toFloat()
        val firstTimeStr = displayData.firstOrNull()?.recorded_at

        if (firstTimeStr != null) {
            val firstDate = sdf.parse(firstTimeStr)
            if (firstDate != null) {
                val firstTimestamp = firstDate.time
                nowX = (now - firstTimestamp).toFloat() / (1000 * 60 * 60)
            }
        }

        val visibleRange = 40f
        val charts = listOf(chartWeather, chartMachine)

        charts.forEach { chart ->
            if (startDateFilter == null && endDateFilter == null) {
                chart.setVisibleXRangeMaximum(visibleRange)
                val centerX = nowX - (visibleRange / 2f)

                // POPRAWKA: moveViewToAnimated zamiast moveViewToXAnimated
                // Parametry: (xIndex, yValue, axis, duration)
                // Ustawiamy yValue na 0f, bo moveViewToAnimated i tak centruje tylko w poziomie,
                // jeśli wykres nie ma zablokowanego przewijania w pionie.
                chart.moveViewToAnimated(centerX, 0f, YAxis.AxisDependency.LEFT, 500)
            }
        }
    }

    private fun updateForecastButtonState() {
        val now = System.currentTimeMillis()

        // Priorytet 1: Jeśli wybrano 1h, prognoza zawsze nie istnieje
        if (currentInterval == 1) {
            setButtonState(enabled = false)
            return
        }

        // Priorytet 2: Ograniczenia wynikające z kalendarza
        if (startDateFilter != null && endDateFilter != null) {

            // Zakres całkowicie w przeszłości
            if (endDateFilter!! < now) {
                setButtonState(enabled = false, isError = true) // czerwony
                return
            }

            // Zakres całkowicie w przyszłości
            if (startDateFilter!! > now) {
                setButtonState(enabled = false)
                isForecastVisible = true
                return
            }
        }

        // Standardowy widok (aktywny)
        setButtonState(enabled = true)
    }
    private fun setButtonState(enabled: Boolean, isError: Boolean = false) {
        btnToggleForecast.isEnabled = enabled

        // efekt "wyszarzenia"
        btnToggleForecast.alpha = if (enabled) 1.0f else 0.4f

        // zmiana koloru ikony
        val color = when {
            isError -> android.graphics.Color.GRAY
            enabled -> android.graphics.Color.BLACK
            else -> android.graphics.Color.GRAY
        }

        btnToggleForecast.iconTint =
            ColorStateList.valueOf(color)
    }
}