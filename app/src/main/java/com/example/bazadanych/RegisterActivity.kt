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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val email = findViewById<EditText>(R.id.editEmail)
        val password = findViewById<EditText>(R.id.editPassword)
        val confirmPassword = findViewById<EditText>(R.id.editConfirmPassword)
        val registerButton = findViewById<Button>(R.id.buttonRegister)
        val loginText = findViewById<TextView>(R.id.buttonLogin)

        viewModel.registerResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Konto utworzone ✅", Toast.LENGTH_SHORT).show()
                finish() // wraca do logowania
            } else {
                Toast.makeText(this, "Błąd rejestracji ❌", Toast.LENGTH_SHORT).show()
            }
        }

        registerButton.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()
            val confirmText = confirmPassword.text.toString()

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