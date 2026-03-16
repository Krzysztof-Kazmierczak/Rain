package com.example.bazadanych

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.editEmail)
        val password = findViewById<EditText>(R.id.editPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val registerButton = findViewById<TextView>(R.id.buttonRegister)

        loginButton.setOnClickListener {
            Toast.makeText(this, "Logowanie...", Toast.LENGTH_SHORT).show()
        }

        registerButton.setOnClickListener {
            Toast.makeText(this, "Rejestracja...", Toast.LENGTH_SHORT).show()
        }
    }
}