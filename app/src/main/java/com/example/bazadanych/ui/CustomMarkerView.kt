package com.example.bazadanych.ui

import android.content.Context
import android.widget.TextView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.FieldHistory
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvMarkerDate: TextView = findViewById(R.id.tvMarkerDate)
    private val tvMarkerValue: TextView = findViewById(R.id.tvMarkerValue)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        // 1. Formatujemy wartość i wstrzykujemy do stringa z zasobów
        val formattedValue = String.format("%.1f", e.y)
        tvMarkerValue.text = context.getString(R.string.marker_value, formattedValue)

        // 2. Pobieramy datę z obiektu FieldHistory i również używamy zasobów
        val history = e.data as? FieldHistory
        if (history != null && !history.recorded_at.isNullOrEmpty()) {
            val timeString = history.recorded_at?.substring(11, 16) ?: ""
            tvMarkerDate.text = context.getString(R.string.marker_time, timeString)
        } else {
            // Tutaj przekazujemy Int, bo w XML masz %1$d (liczba całkowita)
            tvMarkerDate.text = context.getString(R.string.marker_hour_fallback, e.x.toInt())
        }

        // WAŻNE: Wymuszamy na Androidzie przeliczenie wielkości dymka
        super.refreshContent(e, highlight)
    }

    // Ustawia dymek nad palcem
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 20f)
    }
}