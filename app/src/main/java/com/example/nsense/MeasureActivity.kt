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
        // Turns the microphone's digital level into an approximate real-world dB level.
        private const val CALIBRATION_OFFSET_DB = 105.0

        // The audio buffer starts empty, so ignore the first second.
        private const val WARM_UP_MS = 1000L

        // How often the sound type is classified
        private const val CLASSIFY_EVERY_MS = 400L

        // A sound must be detected consistently: its average confidence across all
        // classification rounds must reach this value, otherwise the result is "Other".
        private const val MIN_AVG_CONFIDENCE = 0.2f
    }

    private var audioRecord: AudioRecord? = null
    private var tensorAudio: TensorAudio? = null
    private val handler = Handler(Looper.getMainLooper())

    private var sumMeanSquare = 0.0
    private var windowCount = 0
    private var classificationRounds = 0
    private val categoryScores = mutableMapOf<String, Float>()

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

        findViewById<Button>(R.id.btnStartMeasurement).isEnabled = false
        findViewById<TextView>(R.id.tvMeasurementStatus).text = "Measuring..."

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

                    // Classify the sound type regularly, also after the warm-up second
                    if (elapsed >= WARM_UP_MS && elapsed % CLASSIFY_EVERY_MS == 0L) {
                        classificationRounds++
                        val topScores = NoiseClassifier.classifyTopScores(tensor)
                        for ((category, score) in topScores) {
                            categoryScores[category] = (categoryScores[category] ?: 0f) + score
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

        // Pick the sound with the highest total confidence, but only if it was
        // detected consistently enough. Otherwise the honest answer is "Other".
        val best = categoryScores.maxByOrNull { it.value }
        val detectedSound =
            if (best != null && classificationRounds > 0 &&
                best.value / classificationRounds >= MIN_AVG_CONFIDENCE
            ) {
                best.key
            } else {
                "Other"
            }

        android.util.Log.d(
            "MeasureActivity",
            "scores=$categoryScores rounds=$classificationRounds result=$detectedSound"
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
            startActivity(intent)
            finish()
        }
    }
}