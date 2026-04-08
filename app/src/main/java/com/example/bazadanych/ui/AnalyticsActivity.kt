package com.example.bazadanych.ui

import CustomMarkerView
import android.graphics.Color
import android.graphics.DashPathEffect
import android.os.Bundle
import android.view.MotionEvent
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.api.ApiClient
import com.example.bazadanych.data.db.FieldHistory
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

    private var displayData: List<FieldHistory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

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
        loadDataFromServer(fieldId)

        findViewById<ChipGroup>(R.id.chipGroupInterval).setOnCheckedStateChangeListener { group, checkedIds ->
            val interval = when(checkedIds.first()) {
                R.id.chip3h -> 3
                R.id.chip12h -> 12
                R.id.chipDay -> 24
                else -> 0 // Surowe
            }
            applyAggregation(interval)
        }
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
                // Tutaj musimy rzutować na CheckBox, żeby wyłączyć zaznaczenie
                findViewById<CheckBox>(oldestId).isChecked = false
            }
            if (!activeList.contains(cb.id)) activeList.add(cb.id)
        } else {
            activeList.remove(cb.id)
        }
        updateChartsData()
    }

    private fun applyAggregation(hours: Int) {
        if (hours == 0) {
            displayData = fullHistoryData
        } else {
            // Magia grupowania: bierzemy punkty co X godzin i wyciągamy średnią
            // Dla uproszczenia (skoro dane z API są co 3h), hours / 3 powie nam co ile rekordów brać średnią
            val step = (hours / 3).coerceAtLeast(1)
            displayData = fullHistoryData.windowed(size = step, step = step, partialWindows = true).map { window ->
                FieldHistory(
                    id = window[0].id,
                    field_id = window[0].field_id,
                    temperature = window.mapNotNull { it.temperature }.average(),
                    rain_mm = window.mapNotNull { it.rain_mm }.sum(), // Opady lepiej sumować niż średniować
                    humidity = window.mapNotNull { it.humidity }.average().toInt(),
                    wind_speed = window.mapNotNull { it.wind_speed }.average(),
                    is_forecast = window.last().is_forecast, // Jeśli choć jeden w oknie to prognoza, traktujemy jako prognozę
                    recorded_at = window.last().recorded_at,
                    // ... reszta pól ...
                    machine_speed = window.mapNotNull { it.machine_speed }.average(),
                    wind_deg = window[0].wind_deg,
                    pressure = window[0].pressure,
                    clouds = window[0].clouds
                )
            }
        }
        updateChartsData() // Odśwież wykresy z nowymi danymi
    }

    private fun setupChartStyle(chart: LineChart, isTopChart: Boolean) {
        chart.apply {
            description.isEnabled = false
            setNoDataText("Pobieranie danych...")
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            axisRight.isEnabled = true
            axisLeft.axisMinimum = 0f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                setDrawLabels(true) // Wyświetlamy na obu wykresach
                labelRotationAngle = -45f

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        // Używamy displayData, żeby etykiety pasowały do tego, co widzimy
                        return if (index >= 0 && index < displayData.size) {
                            val date = displayData[index].recorded_at ?: ""
                            if (date.contains(" ")) date.substringAfter(" ").substringBeforeLast(":") else date
                        } else ""
                    }
                }
            }
            setVisibleXRangeMaximum(10f)
            // Przesuń widok na koniec (do najświeższych danych/prognozy)
            moveViewToX(displayData.size.toFloat())
        }
    }

    private fun updateChartsData() {
        // 1. Zabezpieczenie przed pustymi danymi
        if (displayData.isEmpty()) return

        // 2. Konfiguracja markerów (dymków) - robimy to raz dla obu wykresów
        val marker = CustomMarkerView(this, R.layout.view_marker, displayData)
        chartWeather.marker = marker
        chartMachine.marker = marker

        // 3. Rysowanie linii "TERAZ" (pionowa czarna kreska)
        drawNowLine(chartWeather)
        drawNowLine(chartMachine)

        // 4. PRZYGOTOWANIE DANYCH DLA WYKRESU GÓRNEGO (POGODA)
        val weatherData = LineData()

        if (activeWeatherParams.contains(R.id.cbTemp)) {
            addLineWithForecast(weatherData, "Temp (°C)", Color.RED, YAxis.AxisDependency.LEFT) {
                it.temperature?.toFloat()
            }
        }

        if (activeWeatherParams.contains(R.id.cbRain)) {
            addLineWithForecast(weatherData, "Opady (mm)", Color.BLUE, YAxis.AxisDependency.RIGHT) {
                it.rain_mm?.toFloat()
            }
        }

        if (activeWeatherParams.contains(R.id.cbClouds)) {
            addLineWithForecast(weatherData, "Chmury (%)", Color.GRAY, YAxis.AxisDependency.RIGHT) {
                it.clouds?.toFloat()
            }
        }

        // 5. PRZYGOTOWANIE DANYCH DLA WYKRESU DOLNEGO (MASZYNA / WIATR)
        val machineData = LineData()

        if (activeMachineParams.contains(R.id.cbSpeed)) {
            // Prędkość tylko dla historii (is_forecast != 1)
            addLineWithForecast(machineData, "Prędkość (km/h)", Color.parseColor("#4CAF50"), YAxis.AxisDependency.LEFT) {
                if (it.is_forecast == 1) null else it.machine_speed?.toFloat()
            }
        }

        if (activeMachineParams.contains(R.id.cbWind)) {
            addLineWithForecast(machineData, "Wiatr (m/s)", Color.parseColor("#FF9800"), YAxis.AxisDependency.RIGHT) {
                it.wind_speed?.toFloat()
            }
        }

        if (activeMachineParams.contains(R.id.cbHumidity)) {
            addLineWithForecast(machineData, "Wilgotność (%)", Color.parseColor("#00BCD4"), YAxis.AxisDependency.RIGHT) {
                it.humidity?.toFloat()
            }
        }

        // 6. FINALNE ODŚWIEŻENIE WYKRESÓW
        chartWeather.data = weatherData
        chartMachine.data = machineData

        chartWeather.invalidate() // Przerysuj górny
        chartMachine.invalidate() // Przerysuj dolny

        // 7. Synchronizacja widoku (żeby oba pokazywały ten sam zakres po zmianie danych)
        chartWeather.moveViewToX(displayData.size.toFloat())
        chartMachine.moveViewToX(displayData.size.toFloat())
    }

    // Nowa super-funkcja, która automatycznie tnie dane na historię i prognozę
    private fun addLineWithForecast(
        lineData: LineData,
        label: String,
        colorCode: Int,
        axis: YAxis.AxisDependency,
        valueExtractor: (FieldHistory) -> Float?
    ) {
        val pastEntries = mutableListOf<Entry>()
        val futureEntries = mutableListOf<Entry>()

        displayData.forEachIndexed { i, d ->
            val value = valueExtractor(d)
            if (value != null) {
                if (d.is_forecast != 1) {
                    pastEntries.add(Entry(i.toFloat(), value))
                } else {
                    futureEntries.add(Entry(i.toFloat(), value))
                }
            }
        }

        // Łączymy linie (ostatni punkt przeszłości jest pierwszym przyszłości)
        if (pastEntries.isNotEmpty() && futureEntries.isNotEmpty()) {
            futureEntries.add(0, pastEntries.last())
        }

        // 1. Dodajemy linię CIĄGŁĄ (Historia)
        if (pastEntries.isNotEmpty()) {
            lineData.addDataSet(createDataSet(pastEntries, label, colorCode, axis, isDashed = false))
        }

        // 2. Dodajemy linię PRZERYWANĄ (Prognoza)
        if (futureEntries.size > 1) { // Rysuj tylko, gdy faktycznie są jakieś dane z przyszłości
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

            if (label.contains("Opady")) {
                setDrawFilled(true)
                fillColor = Color.BLUE
                fillAlpha = 50 // Przezroczystość
            }

            if (isDashed) {
                enableDashedLine(10f, 10f, 0f) // Magia linii przerywanej dla prognozy!
                setDrawCircles(false) // Mniej kropek na prognozie
            }
        }
    }

    private fun drawNowLine(chart: LineChart) {
        chart.xAxis.removeAllLimitLines()

        // Szukamy pierwszego indeksu, który jest prognozą
        val nowIndex = displayData.indexOfFirst { it.is_forecast == 1 }

        if (nowIndex != -1) {
            val nowLine = com.github.mikephil.charting.components.LimitLine(nowIndex.toFloat(), "TERAZ")
            nowLine.lineColor = Color.BLACK
            nowLine.lineWidth = 2f
            nowLine.enableDashedLine(10f, 5f, 0f)
            nowLine.labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_TOP
            nowLine.textSize = 10f

            chart.xAxis.addLimitLine(nowLine)
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

    private fun loadDataFromServer(fieldId: Int) {
        ApiClient.rainTech.getFieldHistory(fieldId).enqueue(object : Callback<List<FieldHistory>> {
            override fun onResponse(call: Call<List<FieldHistory>>, response: Response<List<FieldHistory>>) {
                if (response.isSuccessful && response.body() != null) {
                    fullHistoryData = response.body()!!
                    displayData = fullHistoryData
                    updateChartsData()
                }
            }
            override fun onFailure(call: Call<List<FieldHistory>>, t: Throwable) {
                chartWeather.setNoDataText("Błąd: ${t.localizedMessage}")
                chartWeather.invalidate()
            }
        })
    }
}