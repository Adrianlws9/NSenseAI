package com.example.nsense

import com.google.firebase.firestore.FirebaseFirestore
import com.example.nsense.data.local.NoiseMeasurement

object FirebaseSync {

    private val db = FirebaseFirestore.getInstance()

    fun uploadMeasurement(measurement: NoiseMeasurement) {

        val data = hashMapOf(
            "id" to measurement.id,
            "noiseLevel" to measurement.noiseLevel,
            "noiseType" to measurement.noiseType,
            "category" to measurement.category,
            "timestamp" to measurement.timestamp,
            "latitude" to measurement.latitude,
            "longitude" to measurement.longitude
        )

        db.collection("noise_measurements")
            .add(data)
            .addOnSuccessListener {
                android.util.Log.d("FirebaseSync", "Uploaded successfully")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FirebaseSync", "Upload failed", e)
            }
    }
}