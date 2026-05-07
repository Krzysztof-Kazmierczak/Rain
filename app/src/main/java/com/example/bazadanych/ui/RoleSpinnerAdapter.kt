package com.example.bazadanych.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class RoleSpinnerAdapter(
    context: Context,
    resource: Int,
    private val items: Array<String>,
    private val minAllowedLevel: Int // Poziom aktualnego użytkownika
) : ArrayAdapter<String>(context, resource, items) {

    // Sprawdza, czy element na danej pozycji jest klikalny
    override fun isEnabled(position: Int): Boolean {
        // Jeśli nasz poziom to np. 2, to pozycje 0 i 1 są zablokowane (false)
        return position >= minAllowedLevel
    }

    // Odpowiada za wygląd elementów na rozwijanej liście
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val tv = view as TextView

        if (position < minAllowedLevel) {
            // Zaszarzenie zablokowanych opcji
            tv.setTextColor(Color.LTGRAY)
        } else {
            // Normalny kolor dla dostępnych opcji
            tv.setTextColor(Color.BLACK)
        }
        return view
    }
}