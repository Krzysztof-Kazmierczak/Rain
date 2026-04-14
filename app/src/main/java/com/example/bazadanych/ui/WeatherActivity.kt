package com.example.bazadanych.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

    private fun loadFieldsAndWeather() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""
        if (email.isEmpty()) return

        // 1. WCZYTAJ CACHE PÓL (Musisz importować CacheHelper na górze pliku!)
        val cachedFields: List<FieldItem>? = com.example.bazadanych.data.local_db.CacheHelper.loadList(this, "WEATHER_FIELDS_CACHE")
        if (cachedFields != null && cachedFields.isNotEmpty()) {
            adapter = WeatherAdapter(cachedFields)
            recyclerWeather.adapter = adapter
        }

        // 2. Pobierz z internetu
        remoteRepo.getAgriculturalFields(email) { fields ->
            runOnUiThread {
                if (fields.isNotEmpty()) {
                    // Zapisz do pamięci na przyszłość
                    com.example.bazadanych.data.local_db.CacheHelper.saveList(this@WeatherActivity, "WEATHER_FIELDS_CACHE", fields)

                    adapter = WeatherAdapter(fields)
                    recyclerWeather.adapter = adapter
                }
            }
        }
    }

    // --- ADAPTER WEWNĘTRZNY ---
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

        private fun displayWeatherData(holder: ViewHolder, data: FieldHistory, isOffline: Boolean) {
            val temp = data.temperature ?: 0.0
            val rain = data.rain_mm ?: 0.0
            val date = data.recorded_at ?: ""

            // --- LOGIKA ETYKIET ---
            // Jeśli offline: pokazać "[Dane Offline] Czas: ..."
            // Jeśli online: pokazać po prostu "AI Doradca: ..."
            val statusLabel = if (isOffline) "[Dane Offline]:" else ""

            holder.tvAdviceMessage.text = "Temp: ${temp}°C, Opady: ${rain}mm"

            // Logika doradcy AI
            val (advice, color) = when {
                rain > 2.0 -> "Nie podlewaj! Spadło wystarczająco deszczu." to "#2196F3"
                temp > 25.0 && rain < 0.5 -> "UWAGA! Susza i upał. Włącz deszczownię." to "#F44336"
                temp < 5.0 -> "Niska temperatura. Rośliny rosną wolniej." to "#9E9E9E"
                else -> "Warunki optymalne. Monitoruj wilgotność gleby." to "#4CAF50"
            }

            // Ustawiamy tytuł: [Dane Offline] Data: Porada LUB Porada (bez daty)
            holder.tvAdviceTitle.text = if (isOffline) {
                "$statusLabel $advice"
            } else {
                "AI Doradca: $advice"
            }

            holder.tvAdviceTitle.setTextColor(Color.parseColor(color))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val field = fields[position]
            holder.tvFieldName.text = field.name ?: "Pole bez nazwy"
            holder.tvFieldCrop.text = "🌾 Uprawa: ${field.cropType}"

            // --- LOGIKA OFFLINE DLA POGODY ---
            val weatherCacheKey = "WEATHER_DATA_FIELD_${field.id}"
            val cachedWeather = com.example.bazadanych.data.local_db.CacheHelper.loadObject<FieldHistory>(holder.itemView.context, weatherCacheKey)

            if (cachedWeather != null) {
                // Jeśli mamy coś w pamięci, pokazujemy to od razu z dopiskiem
                displayWeatherData(holder, cachedWeather, isOffline = true)
            } else {
                holder.tvAdviceTitle.text = "Czekam na dane..."
                holder.tvAdviceMessage.text = "Pobieranie aktualnej pogody..."
            }

            // --- POBIERANIE Z SIECI ---
            ApiClient.rainTech.getCurrentWeather(field.id.toInt()).enqueue(object : Callback<FieldHistory> {
                override fun onResponse(call: Call<FieldHistory>, response: Response<FieldHistory>) {
                    val data = response.body()
                    if (response.isSuccessful && data != null && data.recorded_at != "Brak danych") {
                        // Zapisujemy nową pogodę do cache
                        com.example.bazadanych.data.local_db.CacheHelper.saveObject(holder.itemView.context, weatherCacheKey, data)

                        runOnUiThread {
                            displayWeatherData(holder, data, isOffline = false)
                        }
                    }
                }

                override fun onFailure(call: Call<FieldHistory>, t: Throwable) {
                    // Nic nie robimy, bo dane z cache już wiszą na ekranie dzięki kodowi wyżej
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