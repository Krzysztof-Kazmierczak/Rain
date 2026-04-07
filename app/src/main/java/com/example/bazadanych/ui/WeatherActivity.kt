package com.example.bazadanych.ui

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
import com.example.bazadanych.data.db.FieldEntity
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

        remoteRepo.getAgriculturalFields(email) { fields ->
            runOnUiThread {
                adapter = WeatherAdapter(fields)
                recyclerWeather.adapter = adapter
            }
        }
    }

    // --- ADAPTER WEWNĘTRZNY ---
    inner class WeatherAdapter(private val fields: List<FieldEntity>) : RecyclerView.Adapter<WeatherAdapter.ViewHolder>() {

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
            val field = fields[position]
            holder.tvFieldName.text = field.name ?: "Pole bez nazwy"
            holder.tvFieldCrop.text = "🌾 Uprawa: ${field.cropType}"

            // Uderzamy do Twojego serwera po zapisaną pogodę
            ApiClient.rainTech.getCurrentWeather(field.id.toInt()).enqueue(object : Callback<FieldHistory> {
                override fun onResponse(call: Call<FieldHistory>, response: Response<FieldHistory>) {
                    val data = response.body()
                    if (response.isSuccessful && data != null) {
                        runOnUiThread {
                            holder.tvAdviceTitle.text = "Dane z serwera (${data.recorded_at})"
                            holder.tvAdviceTitle.setTextColor(Color.parseColor("#4CAF50"))
                            holder.tvAdviceMessage.text = "Temp: ${data.temperature}°C, Opady: ${data.rain_mm}mm"

                            // Tu w przyszłości podepniemy naszego AI Doradcę!
                        }
                    } else {
                        runOnUiThread {
                            holder.tvAdviceMessage.text = "Brak danych dla tego pola w bazie."
                        }
                    }
                }

                override fun onFailure(call: Call<FieldHistory>, t: Throwable) {
                    runOnUiThread {
                        holder.tvAdviceTitle.text = "❌ Błąd pobierania"
                        holder.tvAdviceTitle.setTextColor(Color.RED)
                        holder.tvAdviceMessage.text = "Brak połączenia z home.pl: ${t.message}"
                    }
                }
            })
        }

        override fun getItemCount() = fields.size
    }
}