package com.example.bazadanych

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.bazadanych.viewModel.RegisterViewModel

class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels()

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPassword(password: String): Boolean {
        val hasMinLength = password.length >= 5
        val hasDigit = password.any { it.isDigit() }
        return hasMinLength && hasDigit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val email = findViewById<EditText>(R.id.editEmail)
        val password = findViewById<EditText>(R.id.editPassword)
        val confirmPassword = findViewById<EditText>(R.id.editConfirmPassword)
        val registerButton = findViewById<Button>(R.id.buttonRegister)
        val loginText = findViewById<TextView>(R.id.buttonLogin)

        viewModel.registerResult.observe(this) { result ->

            when (result) {

                "OK" -> {
                    Toast.makeText(this, "Konto utworzone ✅", Toast.LENGTH_SHORT).show()
                    finish()
                }

                "EMAIL_EXISTS" -> {
                    Toast.makeText(this, "Email już istnieje ❗", Toast.LENGTH_LONG).show()
                }

                else -> {
                    Toast.makeText(this, "Błąd rejestracji ❌", Toast.LENGTH_SHORT).show()
                }
            }
        }

        registerButton.setOnClickListener {

            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()
            val confirmText = confirmPassword.text.toString()

            // EMAIL
            if (!isValidEmail(emailText)) {
                Toast.makeText(this, "Podaj poprawny email ❗", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // HASŁO WARUNKI
            if (!isValidPassword(passwordText)) {
                Toast.makeText(
                    this,
                    "Hasło musi mieć min. 5 znaków i zawierać cyfrę",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // ZGODNOŚĆ HASEŁ
            if (passwordText != confirmText) {
                Toast.makeText(this, "Hasła się nie zgadzają ❗", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(emailText, passwordText)
        }

        loginText.setOnClickListener {
            finish()
        }
    }
}