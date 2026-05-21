package com.example.bazadanych.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.db.DeviceItem
import com.example.bazadanych.data.db.DeviceTelemetry
import com.example.bazadanych.data.model.MultilineXAxisRenderer
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChartActivity : AppCompatActivity() {

    private lateinit var chartMachine: LineChart
    private lateinit var tvChartLeftLabel: TextView
    private lateinit var btnClearDate: ImageButton

    private val remoteRepo = RainRemoteRepository()

    // Dane do list rozwijanych
    private var deviceList: List<DeviceItem> = emptyList()
    private var selectedDeviceId: Int? = null
    private var selectedParamIndex: Int = 0 // Domyślnie Prędkość aktualna

    // Lista zainicjalizowana dynamicznie w onCreate
    private lateinit var machineParamsNames: List<String>

    // Dane do wykresu
    private var fullTelemetryData: List<DeviceTelemetry> = emptyList()
    private var displayData: List<DeviceTelemetry> = emptyList()

    private var startDateFilter: Long? = null
    private var endDateFilter: Long? = null
    private var currentInterval: Int = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        // Bezpieczne ładowanie tłumaczonych stringów po utworzeniu kontekstu Activity
        machineParamsNames = listOf(
            getString(R.string.chart_param_current_speed),
            getString(R.string.chart_param_target_speed),
            getString(R.string.chart_param_distance),
            getString(R.string.chart_param_work_time),
            getString(R.string.chart_param_battery),
            getString(R.string.chart_param_sim_signal)
        )

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarAnalytics)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        chartMachine = findViewById(R.id.chartMachine)
        tvChartLeftLabel = findViewById(R.id.tvChartLeftLabel)
        btnClearDate = findViewById(R.id.btnClearDate)

        setupChartStyle()
        setupDropdowns()
        setupDateAndIntervalFilters()

        loadDevices()
    }

    private fun setupDropdowns() {
        val spinnerParams = findViewById<AutoCompleteTextView>(R.id.spinnerMachineParams)

        if (spinnerParams == null) {
            Log.e("ChartActivity", "Nie znaleziono spinnerMachineParams! Sprawdź layout.")
            return
        }

        val paramsAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, machineParamsNames)
        spinnerParams.setAdapter(paramsAdapter)

        if (machineParamsNames.isNotEmpty()) {
            spinnerParams.setText(machineParamsNames[0], false)
        }

        spinnerParams.setOnItemClickListener { _, _, position, _ ->
            selectedParamIndex = position
            updateChart()
        }
    }

    private fun setupDateAndIntervalFilters() {
        val btnDateRange = findViewById<ImageButton>(R.id.btnDateRange)

        btnDateRange.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.chart_date_picker_title))
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                startDateFilter = selection.first
                endDateFilter = selection.second + 86399999L // Do końca dnia
                btnClearDate.visibility = View.VISIBLE
                filterAndProcessData()
            }
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        btnClearDate.setOnClickListener {
            startDateFilter = null
            endDateFilter = null
            btnClearDate.visibility = View.GONE
            filterAndProcessData()
        }

        findViewById<ChipGroup>(R.id.chipGroupInterval).setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentInterval = when(checkedId) {
                R.id.chip1h -> 1
                R.id.chip3h -> 3
                R.id.chip12h -> 12
                R.id.chipDay -> 24
                else -> 3
            }
            filterAndProcessData()
        }
    }

    private fun loadDevices() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.getUserDevices(email) { devices ->
            runOnUiThread {
                this.deviceList = devices
                if (devices.isNotEmpty()) {
                    val deviceNames = devices.map { it.name }

                    val spinnerDevices = findViewById<AutoCompleteTextView>(R.id.spinnerDevices)
                    val devicesAdapter = ArrayAdapter(this@ChartActivity, android.R.layout.simple_dropdown_item_1line, deviceNames)
                    spinnerDevices.setAdapter(devicesAdapter)

                    spinnerDevices.setText(deviceNames[0], false)
                    selectedDeviceId = devices[0].id
                    loadTelemetry(devices[0].id)

                    spinnerDevices.setOnItemClickListener { _, _, position, _ ->
                        selectedDeviceId = deviceList[position].id
                        loadTelemetry(deviceList[position].id)
                    }
                } else {
                    Log.w("ChartActivity", "Lista urządzeń jest pusta.")
                }
            }
        }
    }

    private fun loadTelemetry(deviceId: Int) {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        chartMachine.clear()
        chartMachine.setNoDataText(getString(R.string.chart_loading_data))

        remoteRepo.getTelemetryForDevice(deviceId, email) { telemetry ->
            Log.d("API_DEBUG", "Pobrano telemetrię: ${telemetry.size} punktów danych")
            runOnUiThread {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                this.fullTelemetryData = telemetry.sortedBy {
                    try { sdf.parse(it.recordedAt)?.time ?: 0L } catch (e: Exception) { 0L }
                }
                filterAndProcessData()
            }
        }
    }

    private fun filterAndProcessData() {
        if (fullTelemetryData.isEmpty()) {
            displayData = emptyList()
            updateChart()
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val filteredByDate = if (startDateFilter != null && endDateFilter != null) {
            fullTelemetryData.filter {
                val time = try { sdf.parse(it.recordedAt)?.time ?: 0L } catch (e: Exception) { 0L }
                time in startDateFilter!!..endDateFilter!!
            }
        } else {
            fullTelemetryData
        }

        val sampledData = mutableListOf<DeviceTelemetry>()
        var currentSlotKey = ""
        val cal = Calendar.getInstance()

        filteredByDate.forEach { tel ->
            val date = try { sdf.parse(tel.recordedAt) } catch (e: Exception) { null }
            if (date != null) {
                cal.time = date
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                val year = cal.get(Calendar.YEAR)

                val isHourMatch = when (currentInterval) {
                    1 -> true
                    3 -> hour % 3 == 0
                    12 -> hour == 6 || hour == 18
                    24 -> hour == 12
                    else -> true
                }

                if (isHourMatch) {
                    val slotKey = when (currentInterval) {
                        1 -> "$year-$dayOfYear-$hour"
                        3 -> "$year-$dayOfYear-${hour / 3}"
                        12 -> "$year-$dayOfYear-${if (hour == 6) 1 else 2}"
                        24 -> "$year-$dayOfYear-12"
                        else -> "$year-$dayOfYear-$hour"
                    }

                    if (slotKey != currentSlotKey) {
                        sampledData.add(tel)
                        currentSlotKey = slotKey
                    }
                }
            }
        }

        displayData = sampledData
        updateChart()
    }

    private fun setupChartStyle() {
        chartMachine.apply {
            description.isEnabled = false
            setNoDataText(getString(R.string.chart_no_data))
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            axisRight.isEnabled = false
            extraBottomOffset = 15f

            setXAxisRenderer(MultilineXAxisRenderer(viewPortHandler, xAxis, getTransformer(YAxis.AxisDependency.LEFT)))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index < 0 || index >= displayData.size) return ""

                        val dateStr = displayData[index].recordedAt
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val date = try { sdf.parse(dateStr) } catch(e: Exception) { null } ?: return ""

                        val outSdf = if (currentInterval >= 24) {
                            SimpleDateFormat("dd.MM\nyyyy", Locale.getDefault())
                        } else {
                            SimpleDateFormat("HH:mm\ndd.MM", Locale.getDefault())
                        }
                        return outSdf.format(date)
                    }
                }
            }
        }
    }

    private fun updateChart() {
        chartMachine.highlightValues(null)

        if (displayData.isEmpty()) {
            chartMachine.clear()
            tvChartLeftLabel.text = getString(R.string.chart_default_value_label)
            return
        }

        val paramLabel = machineParamsNames[selectedParamIndex]
        val paramColor = Color.parseColor("#1976D2")

        val entries = mutableListOf<Entry>()

        displayData.forEachIndexed { index, tel ->
            val value = when (selectedParamIndex) {
                0 -> tel.currentSpeed ?: 0f
                1 -> tel.targetSpeed ?: 0f
                2 -> tel.distance ?: 0f
                3 -> tel.workTime ?: 0f
                4 -> tel.battery ?: 0f
                5 -> tel.simSignal ?: 0f
                else -> 0f
            }
            entries.add(Entry(index.toFloat(), value))
        }

        if (entries.isNotEmpty()) {
            val dataSet = LineDataSet(entries, paramLabel).apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = paramColor
                setCircleColor(paramColor)
                lineWidth = 2.5f
                circleRadius = 4f
                setDrawValues(false)
                mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            }

            val lineData = LineData(dataSet)
            chartMachine.data = lineData

            tvChartLeftLabel.text = paramLabel
            tvChartLeftLabel.setTextColor(paramColor)
            chartMachine.axisLeft.textColor = paramColor
        } else {
            chartMachine.clear()
        }

        chartMachine.invalidate()
        centerChartOnEnd()
    }

    private fun centerChartOnEnd() {
        if (displayData.isEmpty()) return

        chartMachine.data?.notifyDataChanged()
        chartMachine.notifyDataSetChanged()

        if (startDateFilter == null && endDateFilter == null) {
            val visiblePoints = when(currentInterval) {
                24 -> 14f
                12 -> 30f
                else -> 50f
            }
            chartMachine.setVisibleXRangeMaximum(visiblePoints)
            chartMachine.moveViewToX(displayData.size.toFloat())
        } else {
            chartMachine.setVisibleXRangeMaximum(displayData.size.toFloat())
            chartMachine.moveViewToX(0f)
        }
    }
}