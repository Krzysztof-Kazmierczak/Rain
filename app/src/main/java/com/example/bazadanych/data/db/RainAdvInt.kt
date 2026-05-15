package com.example.bazadanych.data.db

data class RainAdvInt(
    //val id: String = UUID.randomUUID().toString(), // unikalne ID
    val id: Int,
    val urzadzenieId: Int?,
    val userEmail: String?,
    val daneStm: Int?,
    val czyPracuje: Int?,
    val opoznienieAktualizacji: Int,
    val predkoscZadana: Int,
    val opoznionyStartPracy: Boolean,
    val opoznionyStartZwijania: Boolean,
    val opoznioneZakonczenie: Boolean,
    val podlewanieStrefowe: Boolean,
    val rozpoczeciePracy: Boolean,
    val zwijanie: Boolean,
    val predkoscStrefa1: Int,
    val predkoscStrefa2: Int,
    val predkoscStrefa3: Int,
    val czasStm: String?,
    val czasDodania: String?
)