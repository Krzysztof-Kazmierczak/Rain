package com.example.bazadanych.ui // Upewnij się, że paczka jest poprawna

import android.graphics.Color
import android.os.Bundle
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.collections.mapIndexed

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private var fullHistoryData: List<FieldHistory> = emptyList()
    private var fieldId: Int = 1
    private var fieldName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        // 1. Odbierz dane z Intentu (zamiast na sztywno 1)
        fieldId = intent.getIntExtra("FIELD_ID", 1)
        fieldName = intent.getStringExtra("FIELD_NAME") ?: "Pole"

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarAnalytics)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Historia: $fieldName"
        toolbar.setNavigationOnClickListener { finish() }

        lineChart = findViewById(R.id.lineChart)
        setupChartStyle()

        // Obsługa checkboxów
        findViewById<CheckBox>(R.id.cbTemp).setOnCheckedChangeListener { _, _ -> updateChartData() }
        findViewById<CheckBox>(R.id.cbRain).setOnCheckedChangeListener { _, _ -> updateChartData() }
        findViewById<CheckBox>(R.id.cbSpeed).setOnCheckedChangeListener { _, _ -> updateChartData() }

        loadDataFromServer(fieldId)
    }

    private fun setupChartStyle() {
        lineChart.apply {
            description.isEnabled = false
            setNoDataText("Pobieranie danych...")
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                labelRotationAngle = -45f // Pochylamy daty, żeby się zmieściły
                granularity = 1f
            }
            xAxis.setDrawGridLines(false)
            axisRight.isEnabled = true // <-- WŁĄCZAMY PRAWĄ OŚ
            axisLeft.axisMinimum = 0f  // Opcjonalnie: żeby temperatura nie wisiała w powietrzu
            axisRight.axisMinimum = 0f
        }
    }

    private fun updateChartData() {
        if (fullHistoryData.isEmpty()) {
            lineChart.setNoDataText("Brak danych historycznych dla tego pola")
            lineChart.invalidate()
            return
        }

        val lineData = LineData()
        val cbTemp = findViewById<CheckBox>(R.id.cbTemp)
        val cbRain = findViewById<CheckBox>(R.id.cbRain)
        val cbSpeed = findViewById<CheckBox>(R.id.cbSpeed)

        lineChart.axisRight.isEnabled = cbRain.isChecked || cbSpeed.isChecked

        // 1. Temperatura (LEWA OŚ)
        if (cbTemp.isChecked) {
            val tempEntries = fullHistoryData.mapIndexed { i, d -> Entry(i.toFloat(), (d.temperature ?: 0.0).toFloat()) }
            lineData.addDataSet(createDataSet(tempEntries, "Temp (°C)", Color.RED, YAxis.AxisDependency.LEFT))
        }

        // 2. Opady (PRAWA OŚ - dużo mniejsza skala)
        if (cbRain.isChecked) {
            val rainEntries = fullHistoryData.mapIndexed { i, d -> Entry(i.toFloat(), (d.rain_mm ?: 0.0).toFloat()) }
            lineData.addDataSet(createDataSet(rainEntries, "Opady (mm)", Color.BLUE, YAxis.AxisDependency.RIGHT))
        }

        // 3. Prędkość (PRAWA OŚ)
        /*if (cbSpeed.isChecked) {
            val speedEntries = fullHistoryData.mapIndexed { i, d -> Entry(i.toFloat(), (d.machine_speed ?: 0.0).toFloat()) }
            lineData.addDataSet(createDataSet(speedEntries, "Prędkość (m/h)", Color.parseColor("#4CAF50"), YAxis.AxisDependency.RIGHT))
        }*/

        // Ustawienie etykiet osi X (Daty)
        lineChart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < fullHistoryData.size) {
                    // Wycinamy tylko godzinę i minutę z formatu YYYY-MM-DD HH:MM:SS
                    fullHistoryData[index].created_at?.substringAfter(" ")?.substringBeforeLast(":") ?: ""
                } else ""
            }
        }

        lineChart.data = lineData
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    // Dodajemy parametr 'axis' na końcu
    private fun createDataSet(entries: List<Entry>, label: String, colorCode: Int, axis: YAxis.AxisDependency): LineDataSet {
        return LineDataSet(entries, label).apply {
            axisDependency = axis // <-- TO ROZWIĄZUJE TWÓJ PROBLEM
            color = colorCode
            setCircleColor(colorCode)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
    }

    private fun loadDataFromServer(fieldId: Int) {
        ApiClient.rainTech.getFieldHistory(fieldId).enqueue(object : Callback<List<FieldHistory>> {
            override fun onResponse(call: Call<List<FieldHistory>>, response: Response<List<FieldHistory>>) {
                if (response.isSuccessful && response.body() != null) {
                    fullHistoryData = response.body()!!
                    updateChartData()
                }
            }
            override fun onFailure(call: Call<List<FieldHistory>>, t: Throwable) {
                lineChart.setNoDataText("Błąd połączenia: ${t.localizedMessage}")
                lineChart.invalidate()
            }
        })
    }
}