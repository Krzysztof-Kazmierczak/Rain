package com.example.bazadanych.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.bazadanych.R
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {

    private lateinit var userEmail: String
    private val remoteRepo = RainRemoteRepository()

    private lateinit var cbAll: CheckBox
    private lateinit var cbA: CheckBox
    private lateinit var cbB: CheckBox
    private lateinit var cbC: CheckBox
    private lateinit var cbSms: CheckBox
    private lateinit var cbCall: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val sharedPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
        userEmail = sharedPrefs.getString("user_email", "") ?: ""

        if (userEmail.isBlank()) {
            Toast.makeText(this, getString(R.string.settings_no_user), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupLanguageDropdown()
        initUI()
        loadSettings()
    }

    private fun setupLanguageDropdown() {
        val dropdown = findViewById<AutoCompleteTextView>(R.id.languageDropdown)

        // Lista nazw w UI i odpowiadające im kody języków
        val languages = listOf("Polski", "English")
        val languageTags = listOf("pl", "en")

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            languages
        )
        dropdown.setAdapter(adapter)

        // Odczytanie aktualnego języka aplikacji, żeby ustawić właściwą pozycję w dropdownie
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = currentLocales.toLanguageTags() // np. "en" lub puste (wtedy domyślny systemowy)

        val currentIndex = if (currentTag.contains("en")) 1 else 0
        dropdown.setText(languages[currentIndex], false)

        // Reakcja na wybór języka z listy
        dropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedTag = languageTags[position]

            // To wywołanie automatycznie zmieni język i przeładuje Activity!
            val localeList = LocaleListCompat.forLanguageTags(selectedTag)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    private fun initUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarSettings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        cbAll = findViewById(R.id.cbAllNotifications)
        cbA = findViewById(R.id.cbNotifyA)
        cbB = findViewById(R.id.cbNotifyB)
        cbC = findViewById(R.id.cbNotifyC)
        cbSms = findViewById(R.id.cbSms)
        cbCall = findViewById(R.id.cbCall)

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        remoteRepo.getUserSettings(userEmail) { settings ->
            runOnUiThread {
                if (settings != null) {
                    cbAll.isChecked = settings.powiadomienia
                    cbA.isChecked = settings.powiadomienie_A
                    cbB.isChecked = settings.powiadomienie_B
                    cbC.isChecked = settings.powiadomienie_C
                    cbSms.isChecked = settings.sms
                    cbCall.isChecked = settings.dzwonienie
                }
            }
        }
    }

    private fun saveSettings() {
        val settingsMap = mapOf(
            "powiadomienia" to cbAll.isChecked,
            "powiadomienie_A" to cbA.isChecked,
            "powiadomienie_B" to cbB.isChecked,
            "powiadomienie_C" to cbC.isChecked,
            "sms" to cbSms.isChecked,
            "dzwonienie" to cbCall.isChecked
        )

        remoteRepo.saveUserSettings(userEmail, settingsMap) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.settings_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}