package com.example.nsense

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioRecord
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.tensorflow.lite.support.audio.TensorAudio
import kotlin.math.log10

class MeasureActivity : AppCompatActivity() {

    companion object {
        private const val CALIBRATION_OFFSET_DB = 105.0
        private const val WARM_UP_MS = 1000L
        private const val CLASSIFY_EVERY_MS = 400L

        // Main source: its average confidence must reach this, otherwise "Other"
        private const val MIN_AVG_CONFIDENCE = 0.2f

        // Warning sound (siren/alarm): needs this confidence in at least this many readings
        private const val EVENT_MIN_SCORE = 0.35f
        private const val EVENT_MIN_HITS = 2

        // "Also heard" sounds: clear in at least this many readings
        private const val ALSO_MIN_SCORE = 0.25f

        private const val ALSO_MIN_HITS = 2
    }

    private var audioRecord: AudioRecord? = null
    private var tensorAudio: TensorAudio? = null
    private val handler = Handler(Looper.getMainLooper())

    private var sumMeanSquare = 0.0
    private var windowCount = 0
    private var classificationRounds = 0
    private val categoryScores = mutableMapOf<String, Float>()
    private val categoryHits = mutableMapOf<String, Int>()

    private val alsoHits = mutableMapOf<String, Int>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startMeasurement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measure)

        NoiseClassifier.initialize(this)

        val btnStart = findViewById<Button>(R.id.btnStartMeasurement)
        val btnBack = findViewById<Button>(R.id.btnBackHome)

        btnStart.setOnClickListener {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }

        btnBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMeasurement() {
        sumMeanSquare = 0.0
        windowCount = 0
        classificationRounds = 0
        categoryScores.clear()
        categoryHits.clear()
        alsoHits.clear()

        findViewById<Button>(R.id.btnStartMeasurement).isEnabled = false
        findViewById<TextView>(R.id.tvMeasurementStatus).text = "Listening..."

        audioRecord = NoiseClassifier.createAudioRecord()
        tensorAudio = NoiseClassifier.createInputTensorAudio()

        audioRecord?.startRecording()

        val pollInterval = 200L
        val totalDuration = 10000L
        var elapsed = 0L

        val poll = object : Runnable {
            override fun run() {
                val record = audioRecord
                val tensor = tensorAudio

                if (record != null && tensor != null) {
                    tensor.load(record)

                    // Average sound energy (not the loudest peak), skipping the warm-up second
                    if (elapsed >= WARM_UP_MS) {
                        val samples = tensor.tensorBuffer.floatArray
                        var sumSquares = 0.0
                        for (sample in samples) {
                            sumSquares += (sample * sample).toDouble()
                        }
                        if (samples.isNotEmpty()) {
                            sumMeanSquare += sumSquares / samples.size
                            windowCount++
                        }
                    }

                    // Classify regularly after the warm-up second
                    if (elapsed >= WARM_UP_MS && elapsed % CLASSIFY_EVERY_MS == 0L) {
                        classificationRounds++
                        val topScores = NoiseClassifier.classifyTopScores(tensor)
                        for ((category, score) in topScores) {
                            categoryScores[category] = (categoryScores[category] ?: 0f) + score
                            if (score >= EVENT_MIN_SCORE) {
                                categoryHits[category] = (categoryHits[category] ?: 0) + 1
                            }
                            if (score >= ALSO_MIN_SCORE) {
                                alsoHits[category] = (alsoHits[category] ?: 0) + 1
                            }
                        }

                        // Live view of what the model hears right now
                        val top = topScores.maxByOrNull { it.second }
                        findViewById<TextView>(R.id.tvMeasurementStatus).text =
                            if (top != null) {
                                "Hearing: ${top.first} (${(top.second * 100).toInt()}%)"
                            } else {
                                "Listening..."
                            }
                    }
                }

                elapsed += pollInterval

                val secondsLeft = ((totalDuration - elapsed) / 1000).coerceAtLeast(0)
                findViewById<TextView>(R.id.tvTimer).text = "$secondsLeft seconds left"

                if (elapsed < totalDuration) {
                    handler.postDelayed(this, pollInterval)
                } else {
                    finishMeasurement()
                }
            }
        }
        handler.postDelayed(poll, pollInterval)
    }

    private fun finishMeasurement() {
        findViewById<TextView>(R.id.tvMeasurementStatus).text = "Finishing up..."
        findViewById<TextView>(R.id.tvTimer).text = "Getting location..."

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Average level over the whole measurement, converted to approximate dB
        val avgMeanSquare = if (windowCount > 0) sumMeanSquare / windowCount else 0.0
        val noiseDb = if (avgMeanSquare > 0.0) {
            (10 * log10(avgMeanSquare) + CALIBRATION_OFFSET_DB).coerceAtLeast(0.0)
        } else {
            0.0
        }

        // 1) Main source: highest total score, but only if detected consistently
        val best = categoryScores.maxByOrNull { it.value }
        val dominant =
            if (best != null && classificationRounds > 0 &&
                best.value / classificationRounds >= MIN_AVG_CONFIDENCE
            ) {
                best.key
            } else {
                "Other"
            }

        // 2) Warning sounds take priority, even if heard only briefly
        val warning = NoiseSources.warningSounds
            .filter { (categoryHits[it] ?: 0) >= EVENT_MIN_HITS }
            .maxByOrNull { categoryScores[it] ?: 0f }

        val detectedSound = warning ?: dominant

        // 3) Share of each source, for the Result screen and the AI advice
        val totalScore = categoryScores.values.sum()
        val sourcesText = if (detectedSound == "Other" || totalScore <= 0f) {
            "No clear source"
        } else {
            categoryScores.entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(", ") { "${it.key} ${(it.value / totalScore * 100).toInt()}%" }
        }

        val alsoHeard = alsoHits
            .filter { it.value >= ALSO_MIN_HITS && it.key != detectedSound }
            .keys
        val alsoText = if (alsoHeard.isNotEmpty()) {
            "\nAlso heard briefly: " + alsoHeard.joinToString(", ")
        } else {
            ""
        }

        android.util.Log.d(
            "MeasureActivity",
            "scores=$categoryScores hits=$categoryHits rounds=$classificationRounds " +
                    "dominant=$dominant warning=$warning result=$detectedSound"
        )

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, null
        ).addOnSuccessListener { location ->
            val lat = location?.latitude ?: 0.0
            val lng = location?.longitude ?: 0.0

            findViewById<Button>(R.id.btnStartMeasurement).isEnabled = true
            findViewById<TextView>(R.id.tvMeasurementStatus).text = "Ready to measure"

            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("noiseLevel", noiseDb)
            intent.putExtra("latitude", lat)
            intent.putExtra("longitude", lng)
            intent.putExtra("noiseType", detectedSound)
            intent.putExtra("sources", sourcesText + alsoText)
            startActivity(intent)
            finish()
        }
    }

    // Stop the microphone if the user leaves the screen mid-measurement
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // already stopped
        }
        audioRecord?.release()
        audioRecord = null
    }
}