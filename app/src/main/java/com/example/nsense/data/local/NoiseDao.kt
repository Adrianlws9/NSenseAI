package com.example.nsense.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface NoiseDao {

    @Insert
    suspend fun insert(measurement: NoiseMeasurement): Long

    @Query("SELECT * FROM noise_measurements ORDER BY timestamp DESC")
    suspend fun getAllMeasurements(): List<NoiseMeasurement>

    @Query("SELECT * FROM noise_measurements WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getMeasurementsForUser(userId: Int): List<NoiseMeasurement>

    @Query("SELECT * FROM noise_measurements WHERE userId = :userId AND synced = 0")
    suspend fun getUnsynced(userId: Int): List<NoiseMeasurement>

    @Query("UPDATE noise_measurements SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)
}