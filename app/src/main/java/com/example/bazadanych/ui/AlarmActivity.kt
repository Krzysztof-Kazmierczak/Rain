package com.example.bazadanych.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.AlarmItem
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar

class AlarmActivity : AppCompatActivity() {

    private val remoteRepo = RainRemoteRepository()
    private lateinit var userEmail: String

    private lateinit var tvEmptyAlarms: TextView
    private lateinit var rvAlarms: RecyclerView
    private lateinit var btnDeleteAll: Button
    private lateinit var alarmAdapter: AlarmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        val sharedPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
        userEmail = sharedPrefs.getString("user_email", "") ?: ""

        tvEmptyAlarms = findViewById(R.id.tvEmptyAlarms)
        rvAlarms = findViewById(R.id.rvAlarms)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)

        // Przycisk usuwania wszystkiego - ukryty, chyba że chcesz dokodzić osobną logikę admina głównego
        btnDeleteAll.visibility = View.GONE

        rvAlarms.layoutManager = LinearLayoutManager(this)
        setupToolbar()

        // Bezpośrednie ładowanie alarmów
        loadAlarms()
    }

    private fun loadAlarms() {
        remoteRepo.getAlarms(userEmail) { alarms ->
            if (alarms.isEmpty()) {
                tvEmptyAlarms.text = getString(R.string.alarm_empty_list)
                tvEmptyAlarms.visibility = View.VISIBLE
                rvAlarms.visibility = View.GONE
            } else {
                tvEmptyAlarms.visibility = View.GONE
                rvAlarms.visibility = View.VISIBLE

                alarmAdapter = AlarmAdapter(alarms = alarms) { alarm ->
                    confirmDeleteAlarm(alarm)
                }
                rvAlarms.adapter = alarmAdapter
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun confirmDeleteAlarm(alarm: AlarmItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alarm_delete_title))
            .setMessage(getString(R.string.alarm_delete_msg))
            .setPositiveButton(getString(R.string.alarm_delete_positive)) { _, _ ->
                deleteSingleAlarm(alarm.id)
            }
            .setNegativeButton(getString(R.string.alarm_delete_negative), null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deleteSingleAlarm(id: Int) {
        remoteRepo.deleteAlarm(id, userEmail) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.alarm_deleted_success), Toast.LENGTH_SHORT).show()
                loadAlarms()
            } else {
                Toast.makeText(this, getString(R.string.alarm_delete_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}