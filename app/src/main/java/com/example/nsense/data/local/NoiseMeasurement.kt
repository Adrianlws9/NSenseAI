package com.example.nsense.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "noise_measurements")
data class NoiseMeasurement(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val noiseLevel: Double,

    val noiseType: String,

    val category: String,

    val timestamp: Long,

    val latitude: Double = 0.0,

    val longitude: Double = 0.0
)