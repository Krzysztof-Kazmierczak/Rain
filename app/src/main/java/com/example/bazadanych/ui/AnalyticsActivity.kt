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
            axisRight.isEnabled = false
        }
    }

    private fun updateChartData() {
        if (fullHistoryData.isEmpty()) {
            lineChart.setNoDataText("Brak danych historycznych dla tego pola")
            lineChart.invalidate()
            return
        }

        val lineData = LineData()

        // 1. Temperatura
        if (findViewById<CheckBox>(R.id.cbTemp).isChecked) {
            val tempEntries = fullHistoryData.mapIndexed { i, d ->
                Entry(i.toFloat(), (d.temperature ?: 0.0).toFloat())
            }
            lineData.addDataSet(createDataSet(tempEntries, "Temp (°C)", Color.RED))
        }

        // 2. Opady
        if (findViewById<CheckBox>(R.id.cbRain).isChecked) {
            val rainEntries = fullHistoryData.mapIndexed { i, d ->
                Entry(i.toFloat(), (d.rain_mm ?: 0.0).toFloat())
            }
            lineData.addDataSet(createDataSet(rainEntries, "Opady (mm)", Color.BLUE))
        }

        // 3. Prędkość (Poprawione! Nie rzutujemy daty na Float tutaj)
       /* if (findViewById<CheckBox>(R.id.cbSpeed).isChecked) {
            val speedEntries = fullHistoryData.mapIndexed { i, d ->
                Entry(i.toFloat(), (d.created_at ?: 0.0).toFloat())
            }
            lineData.addDataSet(createDataSet(speedEntries, "Prędkość (m/h)", Color.parseColor("#4CAF50")))
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

    private fun createDataSet(entries: List<Entry>, label: String, colorCode: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            color = colorCode
            setCircleColor(colorCode)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            setDrawValues(false) // Wyłączamy cyferki nad kropkami (czytelność!)
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