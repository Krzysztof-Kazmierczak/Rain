package com.example.bazadanych.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.R
import com.example.bazadanych.viewModel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)

        if (prefs.getBoolean("logged_in", false)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.editEmail)
        val password = findViewById<EditText>(R.id.editPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val registerButton = findViewById<TextView>(R.id.buttonRegister)

        // OBSERWUJEMY WYNIK LOGOWANIA
        viewModel.loginResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Zalogowano ✅", Toast.LENGTH_SHORT).show()
                // PRZEJŚCIE DO HOME
                val intent = Intent(this, HomeActivity::class.java)
                val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("logged_in", true)
                    .putString("user_email", email.text.toString().trim())
                    .apply()

                startActivity(intent)
                finish() // zamyka LoginActivity
            } else {
                Toast.makeText(this, "Błąd połączenia ❌", Toast.LENGTH_SHORT).show()
            }
        }

        loginButton.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()
            viewModel.login(emailText, passwordText)
        }

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}