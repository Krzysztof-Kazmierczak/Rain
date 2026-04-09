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

        // 1. Pobieramy nazwę parametru z wykresu
       // val label = chartView?.data?.getDataSetByIndex(highlight?.dataSetIndex ?: 0)?.label ?: "Dane"

        // 2. Formatujemy wartość (np. 22.5)
        tvMarkerValue.text = "${String.format("%.1f", e.y)}"

        // 3. Pobieramy datę z obiektu FieldHistory
        val history = e.data as? FieldHistory
        if (history != null && !history.recorded_at.isNullOrEmpty()) {
            // Wycinamy tylko znaki od 11 do 16 (czyli np. "14:30")
            tvMarkerDate.text = history.recorded_at?.substring(11, 16) ?: ""
        } else {
            tvMarkerDate.text = "Godzina: ${e.x.toInt()}:00"
        }

        // WAŻNE: Wymuszamy na Androidzie przeliczenie wielkości dymka
        super.refreshContent(e, highlight)
    }

    // Ustawia dymek nad palcem
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 20f)
    }
}