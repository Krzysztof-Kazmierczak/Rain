package com.example.bazadanych

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.bazadanych.viewModel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Sprawdź czy użytkownik jest już zalogowany
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val savedToken = prefs.getString("token", null)
        if (savedToken != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val emailEdit = findViewById<EditText>(R.id.editEmail)
        val passwordEdit = findViewById<EditText>(R.id.editPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val registerButton = findViewById<TextView>(R.id.buttonRegister)

        // ✅ Odbieranie wyniku loginu z ViewModel
        viewModel.loginResult.observe(this) { token ->
            if (token != null) {
                // zapis tokena (tutaj prosty "userId|email")
                prefs.edit {
                    putString("token", token)
                }

                Toast.makeText(this, "Zalogowano ✅", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Błędny email lub hasło ❌", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Obsługa kliknięcia Login
        loginButton.setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(email, password)
        }

        // ✅ Przejście do rejestracji
        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}