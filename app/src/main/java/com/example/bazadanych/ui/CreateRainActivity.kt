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

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val hoseInput = findViewById<EditText>(R.id.hoseInput)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val rainSpinner = findViewById<Spinner>(R.id.rainSpinner)
        val addButton = findViewById<Button>(R.id.addButton)

        val rainList = listOf(
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

        rainSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    when (position) {
                        0 -> {
                            nameInput.setText("RM180-R")
                            hoseInput.setText("300")
                        }

                        1 -> {
                            nameInput.setText("RM200-S")
                            hoseInput.setText("450")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        addButton.setOnClickListener {

            val rain = Rain(
                nameInput.text.toString(),
                hoseInput.text.toString(),
                commentInput.text.toString()
            )

            RainStorage.saveRain(this, rain)

            setResult(RESULT_OK)
            finish()
        }
    }
}