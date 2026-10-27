package com.example.nsense.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

@Database(
    entities = [
        NoiseMeasurement::class,
        User::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NoiseDatabase : RoomDatabase() {

    abstract fun noiseDao(): NoiseDao

    abstract fun userDao(): UserDao

    companion object {

        @Volatile
        private var INSTANCE: NoiseDatabase? = null

        fun getDatabase(context: Context): NoiseDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoiseDatabase::class.java,
                    "noise_database"
                ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}