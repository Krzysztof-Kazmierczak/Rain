package com.example.bazadanych.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.api.ApiClient
import com.example.bazadanych.data.db.FieldItem
import com.example.bazadanych.data.db.FieldHistory
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

// =================================================================
// 🔥 KONFIGURACJA DORADCY - WIDEŁKI NA 5 DNI
// =================================================================
object AdvisorConfig {
    const val TEMP_UPAL = 25.0
    const val TEMP_ZIMNO = 7.0

    const val DESZCZ_MIN_24H = 2.0
    const val DESZCZ_MIN_48H = 4.0
    const val DESZCZ_MIN_72H = 5.0
    const val DESZCZ_MIN_96H = 6.0
    const val DESZCZ_MIN_120H = 8.0
}

// Obiekt przechowujący wynik analizy
data class AdviceResult(
    val title: String,
    val message: String,
    val colorHex: String,
    val urgencyScore: Int
)

// =================================================================
// 🔥 SILNIK LOGIKI AI - Przeniesiony poza widok dla łatwego sortowania
// =================================================================
object AIAdvisor {
    fun analyzeWeather(context: Context, historyAndForecast: List<FieldHistory>?): AdviceResult {
        if (historyAndForecast.isNullOrEmpty()) {
            return AdviceResult(
                context.getString(R.string.advisor_waiting_title),
                context.getString(R.string.advisor_waiting_desc),
                "#9E9E9E",
                99
            )
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowMs = System.currentTimeMillis()

        val forecastData = historyAndForecast.filter {
            val time = try { sdf.parse(it.recorded_at ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
            time >= nowMs || it.is_forecast == 1
        }.sortedBy {
            try { sdf.parse(it.recorded_at ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
        }

        val msInDay = 24 * 60 * 60 * 1000L
        val limits = (1..5).map { nowMs + (it * msInDay) }

        fun getTime(dateStr: String?): Long = try { sdf.parse(dateStr ?: "")?.time ?: 0L } catch (e: Exception) { 0L }

        val list24h = forecastData.filter { getTime(it.recorded_at) <= limits[0] }
        val list48h = forecastData.filter { getTime(it.recorded_at) in (limits[0] + 1)..limits[1] }
        val list72h = forecastData.filter { getTime(it.recorded_at) in (limits[1] + 1)..limits[2] }
        val list96h = forecastData.filter { getTime(it.recorded_at) in (limits[2] + 1)..limits[3] }
        val list120h = forecastData.filter { getTime(it.recorded_at) in (limits[3] + 1)..limits[4] }

        val rain24h = list24h.mapNotNull { it.rain_mm }.sum()
        val rain48h = list48h.mapNotNull { it.rain_mm }.sum()
        val rain72h = list72h.mapNotNull { it.rain_mm }.sum()
        val rain96h = list96h.mapNotNull { it.rain_mm }.sum()
        val rain120h = list120h.mapNotNull { it.rain_mm }.sum()

        // Suma opadów do wyświetlenia zawsze na końcu komunikatu
        val totalRain5Days = rain24h + rain48h + rain72h + rain96h + rain120h
        val rainInfoString = context.getString(R.string.advisor_total_rain_format, totalRain5Days)

        val avgTemp24h = list24h.mapNotNull { it.temperature }.average().let { if (it.isNaN()) 15.0 else it }
        val maxTemp3Days = forecastData.take(24).mapNotNull { it.temperature }.maxOrNull() ?: 15.0
        val maxTemp5Days = forecastData.take(40).mapNotNull { it.temperature }.maxOrNull() ?: 15.0

        // =========================================================
        // DRZEWO DECYZYJNE (Od największej ulewy do największej suszy)
        // =========================================================
        val advice: AdviceResult = when {
            // 1. DZIŚ / JUTRO MOCNY DESZCZ
            rain24h >= AdvisorConfig.DESZCZ_MIN_24H -> AdviceResult(
                context.getString(R.string.advisor_title_dont_water),
                context.getString(R.string.advisor_msg_dont_water, rain24h),
                "#2196F3",
                urgencyScore = 10
            )

            // 2. ZA 2 DNI DESZCZ
            rain48h >= AdvisorConfig.DESZCZ_MIN_48H -> {
                if (avgTemp24h >= AdvisorConfig.TEMP_UPAL) {
                    AdviceResult(
                        context.getString(R.string.advisor_title_water_moderate),
                        context.getString(R.string.advisor_msg_water_moderate, rain48h, avgTemp24h),
                        "#FF9800",
                        urgencyScore = 4
                    )
                } else {
                    AdviceResult(
                        context.getString(R.string.advisor_title_control_humidity),
                        context.getString(R.string.advisor_msg_control_humidity, rain48h),
                        "#CDDC39",
                        urgencyScore = 8
                    )
                }
            }

            // 3. ZA 3 DNI DESZCZ
            rain72h >= AdvisorConfig.DESZCZ_MIN_72H -> {
                if (maxTemp3Days >= AdvisorConfig.TEMP_UPAL) {
                    AdviceResult(
                        context.getString(R.string.advisor_title_water),
                        context.getString(R.string.advisor_msg_water, maxTemp3Days),
                        "#E91E63",
                        urgencyScore = 3
                    )
                } else {
                    AdviceResult(
                        context.getString(R.string.advisor_title_plan_efficiently),
                        context.getString(R.string.advisor_msg_plan_efficiently, rain72h),
                        "#8BC34A",
                        urgencyScore = 7
                    )
                }
            }

            // 4. ZA 4 DNI DESZCZ
            rain96h >= AdvisorConfig.DESZCZ_MIN_96H -> {
                if (maxTemp5Days >= AdvisorConfig.TEMP_UPAL) {
                    AdviceResult(
                        context.getString(R.string.advisor_title_water_standard),
                        context.getString(R.string.advisor_msg_water_standard),
                        "#FF5722",
                        urgencyScore = 2
                    )
                } else {
                    AdviceResult(
                        context.getString(R.string.advisor_title_optimal_cycle),
                        context.getString(R.string.advisor_msg_optimal_cycle),
                        "#4CAF50",
                        urgencyScore = 6
                    )
                }
            }

            // 5. ZA 5 DNI DESZCZ
            rain120h >= AdvisorConfig.DESZCZ_MIN_120H -> {
                if (maxTemp5Days >= AdvisorConfig.TEMP_UPAL) {
                    AdviceResult(
                        context.getString(R.string.advisor_title_heat_alert),
                        context.getString(R.string.advisor_msg_heat_alert),
                        "#F44336",
                        urgencyScore = 1
                    )
                } else {
                    AdviceResult(
                        context.getString(R.string.advisor_title_stable_conditions),
                        context.getString(R.string.advisor_msg_stable_conditions),
                        "#4CAF50",
                        urgencyScore = 5
                    )
                }
            }

            // 6. CAŁKOWITA SUSZA (Brak opadów przez 5 dni)
            maxTemp5Days >= AdvisorConfig.TEMP_UPAL -> AdviceResult(
                context.getString(R.string.advisor_title_critical_drought),
                context.getString(R.string.advisor_msg_critical_drought, maxTemp5Days),
                "#B71C1C",
                urgencyScore = 0
            )

            // 7. CHŁODNE DNI BEZ DESZCZU
            avgTemp24h <= AdvisorConfig.TEMP_ZIMNO -> AdviceResult(
                context.getString(R.string.advisor_title_slow_growth),
                context.getString(R.string.advisor_msg_slow_growth, avgTemp24h),
                "#9E9E9E",
                urgencyScore = 9
            )

            // 8. OPTYMALNIE (Norma)
            else -> AdviceResult(
                context.getString(R.string.advisor_title_optimal),
                context.getString(R.string.advisor_msg_optimal),
                "#4CAF50",
                urgencyScore = 5
            )
        }

        return advice.copy(message = advice.message + rainInfoString)
    }
}

class WeatherActivity : AppCompatActivity() {

    private val remoteRepo = RainRemoteRepository()
    private lateinit var recyclerWeather: RecyclerView
    private lateinit var adapter: WeatherAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarWeather)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerWeather = findViewById(R.id.recyclerWeather)
        recyclerWeather.layoutManager = LinearLayoutManager(this)

        loadFieldsAndWeather()
    }

    private fun sortFieldsByUrgency(fields: List<FieldItem>): List<FieldItem> {
        return fields.sortedBy { field ->
            val weatherCacheKey = "WEATHER_HISTORY_LIST_FIELD_${field.id}"
            val cachedWeatherList = com.example.bazadanych.data.local_db.CacheHelper.loadList<FieldHistory>(this, weatherCacheKey)

            val advice = AIAdvisor.analyzeWeather(this, cachedWeatherList)
            advice.urgencyScore
        }
    }

    private fun loadFieldsAndWeather() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        if (email.isEmpty()) return

        // 1. WCZYTANIE CACHE I WSTĘPNE POSORTOWANIE
        val cachedFields: List<FieldItem>? = com.example.bazadanych.data.local_db.CacheHelper.loadList(this, "WEATHER_FIELDS_CACHE")
        adapter = WeatherAdapter(sortFieldsByUrgency(cachedFields ?: emptyList()))
        recyclerWeather.adapter = adapter

        // 2. POBRANIE PÓL Z SIECI, POSORTOWANIE I AKTUALIZACJA
        remoteRepo.getAgriculturalFields(email) { fields ->
            runOnUiThread {
                com.example.bazadanych.data.local_db.CacheHelper.saveList(this@WeatherActivity, "WEATHER_FIELDS_CACHE", fields)
                adapter = WeatherAdapter(sortFieldsByUrgency(fields))
                recyclerWeather.adapter = adapter
            }
        }
    }

    // --- ADAPTER ---
    inner class WeatherAdapter(private val fields: List<FieldItem>) : RecyclerView.Adapter<WeatherAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFieldName: TextView = view.findViewById(R.id.tvFieldName)
            val tvFieldCrop: TextView = view.findViewById(R.id.tvFieldCrop)
            val adviceContainer: LinearLayout = view.findViewById(R.id.adviceContainer)
            val tvAdviceTitle: TextView = view.findViewById(R.id.tvAdviceTitle)
            val tvAdviceMessage: TextView = view.findViewById(R.id.tvAdviceMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_weather_field, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val context = holder.itemView.context
            val email = context.getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
            val field = fields[position]

            holder.tvFieldName.text = field.name ?: context.getString(R.string.field_no_name)
            holder.tvFieldCrop.text = context.getString(R.string.field_crop_format, field.cropType)

            val weatherCacheKey = "WEATHER_HISTORY_LIST_FIELD_${field.id}"
            val cachedWeatherList: List<FieldHistory>? = com.example.bazadanych.data.local_db.CacheHelper.loadList(context, weatherCacheKey)

            if (!cachedWeatherList.isNullOrEmpty()) {
                val advice = AIAdvisor.analyzeWeather(context, cachedWeatherList)
                holder.tvAdviceTitle.text = context.getString(R.string.offline_advisor_title_format, advice.title)
                holder.tvAdviceTitle.setTextColor(Color.parseColor(advice.colorHex))
                holder.tvAdviceMessage.text = advice.message
            } else {
                val initAdvice = AIAdvisor.analyzeWeather(context, null)
                holder.tvAdviceTitle.text = initAdvice.title
                holder.tvAdviceMessage.text = initAdvice.message
            }

            ApiClient.rainTech.getFieldHistory(field.id.toInt(), email).enqueue(object : Callback<List<FieldHistory>> {
                override fun onResponse(call: Call<List<FieldHistory>>, response: Response<List<FieldHistory>>) {
                    val dataList = response.body()
                    if (response.isSuccessful && !dataList.isNullOrEmpty()) {
                        com.example.bazadanych.data.local_db.CacheHelper.saveList(context, weatherCacheKey, dataList)

                        runOnUiThread {
                            val advice = AIAdvisor.analyzeWeather(context, dataList)
                            holder.tvAdviceTitle.text = context.getString(R.string.online_advisor_title_format, advice.title)
                            holder.tvAdviceTitle.setTextColor(Color.parseColor(advice.colorHex))
                            holder.tvAdviceMessage.text = advice.message
                        }
                    }
                }
                override fun onFailure(call: Call<List<FieldHistory>>, t: Throwable) {
                    Log.e("API_ERROR", "Błąd połączenia: ${t.message}")
                }
            })

            holder.itemView.setOnClickListener {
                val intent = Intent(this@WeatherActivity, AnalyticsActivity::class.java)
                intent.putExtra("FIELD_ID", field.id.toInt())
                intent.putExtra("FIELD_NAME", field.name)
                startActivity(intent)
            }
        }

        override fun getItemCount() = fields.size
    }
}