package com.example.bazadanych.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.data.db.Rain
import com.example.bazadanych.data.db.RainStorage
import com.google.android.material.appbar.MaterialToolbar

class CreateRainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_rain)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val hoseInput = findViewById<EditText>(R.id.hoseInput)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val rainSpinner = findViewById<Spinner>(R.id.rainSpinner)
        val addButton = findViewById<Button>(R.id.addButton)

        val rainList = listOf(
            "Wybierz deszczownię",
            "RM180-R (300 m)",
            "RM200-S (450 m)"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            rainList
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rainSpinner.adapter = adapter
        rainSpinner.setSelection(0)

        rainSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    when (position) {
                        0 -> {// Wybierz deszczownię
                            nameInput.setText("")
                            hoseInput.setText("")
                        }

                        1 -> {
                            nameInput.setText("RM180-R")
                            hoseInput.setText("300")

                        }
                        2 -> {
                            nameInput.setText("RM200-S")
                            hoseInput.setText("450")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        addButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val length = hoseInput.text.toString().trim()
            val comment = commentInput.text.toString().trim()

            if (name.isEmpty() || length.isEmpty()) {
                Toast.makeText(this, "Wypełnij nazwę i długość węża", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rain = Rain(name = name, hoseLength = length, comment = comment)
            RainStorage.saveRain(this, rain)

            setResult(RESULT_OK)
            finish()
        }
    }
}