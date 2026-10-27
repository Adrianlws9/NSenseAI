package com.example.nsense.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface NoiseDao {

    @Insert
    suspend fun insert(measurement: NoiseMeasurement)

    @Query("SELECT * FROM noise_measurements ORDER BY timestamp DESC")
    suspend fun getAllMeasurements(): List<NoiseMeasurement>
}