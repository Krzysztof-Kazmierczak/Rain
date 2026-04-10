package com.example.bazadanych.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FieldDao {
    // Pobieranie historii dla konkretnego pola
    @Query("SELECT * FROM field_history WHERE fieldId = :fId ORDER BY recordedAt ASC")
    suspend fun getHistoryForField(fId: Int): List<FieldEntity>

    // Wstawianie listy rekordów
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<FieldEntity>)

    // Usuwanie starych rekordów dla danego pola
    @Query("DELETE FROM field_history WHERE fieldId = :fId")
    suspend fun deleteByFieldId(fId: Int)

    // Transakcja: najpierw usuń stare dane, potem wstaw nowe (żeby nie było duplikatów)
    @Transaction
    suspend fun deleteAndInsert(fId: Int, list: List<FieldEntity>) {
        deleteByFieldId(fId)
        insertAll(list)
    }
}