package com.example.bazadanych.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bazadanych.data.repository.UserRepository

class RegisterViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _registerResult = MutableLiveData<String>()
    val registerResult: LiveData<String> = _registerResult

    fun register(email: String, password: String) {
        repository.registerUser(email, password) { result ->
            _registerResult.postValue(result)
        }
    }
}