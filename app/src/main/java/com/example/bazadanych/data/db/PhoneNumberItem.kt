package com.example.bazadanych.data.db

/** Jeden numer telefonu przypisany do maszyny (tabela phone_number). */
data class PhoneNumberItem(
    val id: Int,
    val kierunkowy: String,
    val numer: String,
    val poziomDostepu: Int,
    val zgodaSms: Boolean,
    val potwierdzono: String?,
    val isAdminNumber: Boolean
) {
    val pelnyNumer: String get() = "+$kierunkowy $numer"
}

data class PhoneNumbersResponse(
    val canManage: Boolean,
    val myLevel: Int,
    val adminNumber: String,
    val numbers: List<PhoneNumberItem>
)