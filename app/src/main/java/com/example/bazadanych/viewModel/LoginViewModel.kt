package com.example.bazadanych.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bazadanych.data.repository.UserRepository

class LoginViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _loginResult = MutableLiveData<Boolean>()
    val loginResult: LiveData<Boolean> = _loginResult

    fun login(email: String, password: String) {
        repository.loginUser(email, password) { success ->
            _loginResult.postValue(success)
        }
    }
}