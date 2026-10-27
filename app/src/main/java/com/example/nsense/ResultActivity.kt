package com.example.nsense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nsense.data.local.NoiseDatabase
import com.example.nsense.data.local.NoiseMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_result)

        val database = NoiseDatabase.getDatabase(this)

        val btnSaveResult = findViewById<Button>(R.id.btnSaveResult)
        val btnBackHome = findViewById<Button>(R.id.btnBackHome)

        val tvDecibel = findViewById<TextView>(R.id.tvDecibel)
        val tvNoiseCategory = findViewById<TextView>(R.id.tvNoiseCategory)
        val tvNoiseType = findViewById<TextView>(R.id.tvNoiseType)
        val tvRecommendation = findViewById<TextView>(R.id.tvRecommendation)

        // Real values passed from MeasureActivity
        val noiseLevel = intent.getDoubleExtra("noiseLevel", 0.0)
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val noiseType = intent.getStringExtra("noiseType") ?: "Other"
        val sources = intent.getStringExtra("sources") ?: "No clear source"

        // The ML result changes the verdict: the sound source adds to the rating
        val penalty = NoiseSources.penalty(noiseType)
        val rating = noiseLevel + penalty

        val category = when {
            rating < 50 -> "Low"
            rating < 70 -> "Moderate"
            else -> "High"
        }

        val ratingText = if (penalty > 0) {
            "Impact rating: ${rating.toInt()} (+$penalty for $noiseType)"
        } else {
            "Impact rating: ${rating.toInt()}"
        }

        tvDecibel.text = String.format("%.1f dB", noiseLevel)
        tvNoiseCategory.text = category.uppercase()
        tvNoiseType.text = "Detected sound: $noiseType\nSources: $sources\n$ratingText"

        // Remember this reading so the Home screen can show it
        LastReading.save(this, noiseLevel, category, noiseType)

        // AI recommendation (built-in advice if offline or the call fails)
        if (NetworkUtils.isInternetAvailable(this)) {
            tvRecommendation.text = "AI recommendation:\nGenerating advice..."
            lifecycleScope.launch {
                val advice = RecommendationService.getRecommendation(
                    noiseLevel, rating, noiseType, category, sources
                )
                tvRecommendation.text = if (advice != null) {
                    "AI recommendation:\n$advice"
                } else {
                    "Recommendation:\n" +
                            RecommendationService.fallback(category, noiseType)
                }
            }
        } else {
            tvRecommendation.text =
                "Recommendation:\n" + RecommendationService.fallback(category, noiseType)
        }

        // AI alert: only for loud or dangerous sounds
        if (RecommendationService.shouldAlert(rating, noiseType)) {
            val alertTitle = RecommendationService.alertTitle(noiseLevel, noiseType)

            if (NetworkUtils.isInternetAvailable(this)) {
                lifecycleScope.launch {
                    val alertMessage =
                        RecommendationService.getAlertMessage(noiseLevel, noiseType)
                    NotificationHelper.showAlert(this@ResultActivity, alertTitle, alertMessage)
                }
            } else {
                NotificationHelper.showAlert(
                    this, alertTitle, RecommendationService.fallbackAlert(noiseType)
                )
            }
        }

        btnSaveResult.setOnClickListener {

            // Stops accidental double saves
            btnSaveResult.isEnabled = false

            val measurement = NoiseMeasurement(
                noiseLevel = noiseLevel,
                noiseType = noiseType,
                category = category,
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                userId = SessionManager.userId(this)
            )

            CoroutineScope(Dispatchers.IO).launch {

                val rowId = database.noiseDao().insert(measurement).toInt()
                val saved = measurement.copy(id = rowId)

                if (NetworkUtils.isInternetAvailable(this@ResultActivity)) {
                    FirebaseSync.uploadMeasurement(saved) {
                        CoroutineScope(Dispatchers.IO).launch {
                            database.noiseDao().markSynced(rowId)
                        }
                    }
                } else {
                    android.util.Log.d("Sync", "No internet: saved locally, will upload later")
                }

                runOnUiThread {
                    Toast.makeText(
                        this@ResultActivity,
                        "Measurement saved",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnBackHome.setOnClickListener {

            val intent = Intent(this, HomeActivity::class.java)

            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)

            finish()
        }
    }
}