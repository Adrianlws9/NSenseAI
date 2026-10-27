package com.example.nsense

import android.content.Context
import com.example.nsense.data.local.NoiseDatabase
import com.example.nsense.data.local.NoiseMeasurement
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirebaseSync {

    private val db = FirebaseFirestore.getInstance()

    // Readings currently being uploaded, so the same one is never sent twice
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // Note: no user id is uploaded. The cloud copy is anonymous noise data.
    fun uploadMeasurement(measurement: NoiseMeasurement, onSuccess: () -> Unit = {}) {

        if (!inFlight.add(measurement.id)) return

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
                inFlight.remove(measurement.id)
                android.util.Log.d("FirebaseSync", "Uploaded successfully")
                onSuccess()
            }
            .addOnFailureListener { e ->
                inFlight.remove(measurement.id)
                android.util.Log.e("FirebaseSync", "Upload failed", e)
            }
    }

    // Re-sends this user's readings that never reached the cloud
    suspend fun syncPending(context: Context, userId: Int) {
        if (!NetworkUtils.isInternetAvailable(context)) return

        val dao = NoiseDatabase.getDatabase(context).noiseDao()
        for (m in dao.getUnsynced(userId)) {
            uploadMeasurement(m) {
                CoroutineScope(Dispatchers.IO).launch { dao.markSynced(m.id) }
            }
        }
    }
}