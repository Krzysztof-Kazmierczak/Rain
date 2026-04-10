package com.example.bazadanych.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// DODANE FieldEntity::class do listy encji!
@Database(entities = [RainEntity::class, FieldEntity::class], version = 1, exportSchema = false)
abstract class DataBase : RoomDatabase() {

    abstract fun rainDao(): RainDao
    abstract fun fieldDao(): FieldDao // DODANE!

    companion object {
        @Volatile
        private var INSTANCE: DataBase? = null

        fun getDatabase(context: Context): DataBase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DataBase::class.java,
                    "agro_database"
                )
                    .fallbackToDestructiveMigration() // Zabezpieczenie przed crashami przy zmianie struktury bazy
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}