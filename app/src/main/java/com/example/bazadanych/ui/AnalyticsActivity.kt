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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        lineChart = findViewById(R.id.lineChart)
        setupChartStyle()

        // Obsługa checkboxów
        val checkBoxes = listOf(R.id.cbTemp, R.id.cbRain, R.id.cbSpeed)
        checkBoxes.forEach { id ->
            findViewById<CheckBox>(id).setOnCheckedChangeListener { _, _ -> updateChartData() }
        }

        loadDataFromServer(fieldId = 1) // Na razie na sztywno ID pola 1
    }

    private fun setupChartStyle() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            axisRight.isEnabled = false // Wyłączamy prawą oś dla czytelności
        }
    }

    private fun updateChartData() {
        if (fullHistoryData.isEmpty()) return

        val lineData = LineData()

        // 1. Linia Temperatury (Czerwona)
        if (findViewById<CheckBox>(R.id.cbTemp).isChecked) {
            val entries = fullHistoryData.mapIndexed { i, d -> Entry(i.toFloat(), d.temperature.toFloat()) }
            lineData.addDataSet(createDataSet(entries, "Temp", Color.RED))
        }

        // 2. Linia Opadów (Niebieska)
        if (findViewById<CheckBox>(R.id.cbRain).isChecked) {
            val entries = fullHistoryData.mapIndexed { i, d -> Entry(i.toFloat(), d.rain_mm.toFloat()) }
            lineData.addDataSet(createDataSet(entries, "Opady", Color.BLUE))
        }

        // 3. Linia Prędkości (Zielona)
        if (findViewById<CheckBox>(R.id.cbSpeed).isChecked) {
            val entries = fullHistoryData.mapIndexed { i, d -> Entry(i.toFloat(), d.machine_speed.toFloat()) }
            lineData.addDataSet(createDataSet(entries, "Prędkość", Color.parseColor("#4CAF50")))
        }

        lineChart.data = lineData
        lineChart.invalidate() // Odświeżenie
    }

    private fun createDataSet(entries: List<Entry>, label: String, colorCode: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            color = colorCode
            setCircleColor(colorCode)
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            valueTextSize = 0f // Ukrywamy wartości nad punktami, żeby nie było tłoczno
            mode = LineDataSet.Mode.CUBIC_BEZIER // Wygładzone linie!
        }
    }

    private fun loadDataFromServer(fieldId: Int) {
        ApiClient.rainTech.getFieldHistory(fieldId).enqueue(object : Callback<List<FieldHistory>> {
            override fun onResponse(call: Call<List<FieldHistory>>, response: Response<List<FieldHistory>>) {
                if (response.isSuccessful && response.body() != null) {
                    fullHistoryData = response.body()!!
                    runOnUiThread {
                        updateChartData() // Rysujemy wykres po pobraniu!
                    }
                }
            }

            override fun onFailure(call: Call<List<FieldHistory>>, t: Throwable) {
                // Tutaj np. Toast z informacją o błędzie
            }
        })
    }
}