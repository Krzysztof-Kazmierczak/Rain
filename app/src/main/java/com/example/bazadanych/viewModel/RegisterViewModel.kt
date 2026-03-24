package com.example.bazadanych.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bazadanych.data.repository.UserRepository

class RegisterViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _registerResult = MutableLiveData<Boolean>()
    val registerResult: LiveData<Boolean> = _registerResult

    fun register(email: String, password: String) {
        repository.registerUser(email, password) { success ->
            _registerResult.postValue(success)
        }
    }
}