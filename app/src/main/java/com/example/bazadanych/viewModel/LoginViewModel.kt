package com.example.bazadanych.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bazadanych.data.repository.UserRepository

class LoginViewModel : ViewModel() {

    private val repository = UserRepository()

    // LiveData do obserwowania tokena
    private val _loginResult = MutableLiveData<String?>()
    val loginResult: LiveData<String?> = _loginResult

    /**
     * Funkcja logowania
     * @param email email użytkownika
     * @param password hasło użytkownika
     */
    fun login(email: String, password: String) {

        // Wywołanie repozytorium, które robi request HTTP
        repository.loginUser(email, password) { token ->

            // jeśli token nie jest pusty i wygląda sensownie, wysyłamy go do LiveData
            if (token != null && token.length > 10) {
                _loginResult.postValue(token)
            } else {
                _loginResult.postValue(null) // brak tokena → logowanie nieudane
            }
        }
    }
}