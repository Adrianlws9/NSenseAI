package com.example.nsense

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.media.AudioRecord
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.log10

class MonitorActivity : AppCompatActivity() {

    companion object {
        private const val CALIBRATION_OFFSET_DB = 105.0   // same as MeasureActivity
        private const val HIGH_DB = 70.0
        private const val SUSTAINED_SECONDS = 30          // alert if this many seconds average >= HIGH_DB
        private const val ALERT_GAP_MS = 120_000L         // at most one alert every 2 minutes
        private const val MAX_SECONDS = 600               // auto-stop after 10 minutes
        private const val MIN_SOURCE_CONFIDENCE = 0.2f
    }

    private var audioRecord: AudioRecord? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null
    private var running = false

    private var seconds = 0
    private var highSeconds = 0
    private var sumDb = 0.0
    private var peakDb = 0.0
    private val recent = ArrayDeque<Double>()
    private val sourceSeconds = mutableMapOf<String, Int>()
    private var lastAlertAt = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMonitoring()
        } else {
            Toast.makeText(this, "Microphone permission is needed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)

        findViewById<Button>(R.id.btnMonitorToggle).setOnClickListener {
            if (running) {
                stopMonitoring()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<Button>(R.id.btnMonitorBack).setOnClickListener {
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoring() {
        NoiseClassifier.initialize(this)
        val record = NoiseClassifier.createAudioRecord()
        val tensor = NoiseClassifier.createInputTensorAudio()
        if (record == null || tensor == null) {
            Toast.makeText(this, "Could not start the microphone", Toast.LENGTH_SHORT).show()
            return
        }

        seconds = 0
        highSeconds = 0
        sumDb = 0.0
        peakDb = 0.0
        recent.clear()
        sourceSeconds.clear()
        lastAlertAt = 0L

        audioRecord = record
        record.startRecording()
        running = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        findViewById<Button>(R.id.btnMonitorToggle).text = "Stop Monitoring"
        findViewById<TextView>(R.id.tvMonitorSummary).text = ""
        findViewById<TextView>(R.id.tvMonitorStatus).text = "Monitoring..."

        val runnable = object : Runnable {
            override fun run() {
                if (!running) return

                tensor.load(record)

                // Same formula as the Measure screen
                val samples = tensor.tensorBuffer.floatArray
                var sumSquares = 0.0
                for (s in samples) sumSquares += (s * s).toDouble()
                val meanSquare = if (samples.isNotEmpty()) sumSquares / samples.size else 0.0
                val db = if (meanSquare > 0.0) {
                    (10 * log10(meanSquare) + CALIBRATION_OFFSET_DB).coerceAtLeast(0.0)
                } else {
                    0.0
                }

                // Strongest sound source this second
                val top = NoiseClassifier.classifyTopScores(tensor).maxByOrNull { it.second }
                val source = if (top != null && top.second >= MIN_SOURCE_CONFIDENCE) top.first else "Other"
                sourceSeconds[source] = (sourceSeconds[source] ?: 0) + 1

                seconds++
                sumDb += db
                if (db > peakDb) peakDb = db
                if (db >= HIGH_DB) highSeconds++

                recent.addLast(db)
                if (recent.size > SUSTAINED_SECONDS) recent.removeFirst()

                updateScreen(db, source)
                checkSustainedAlert()

                if (seconds >= MAX_SECONDS) {
                    stopMonitoring()
                } else {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        tick = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun updateScreen(db: Double, source: String) {
        val tvLevel = findViewById<TextView>(R.id.tvMonitorLevel)
        tvLevel.text = "${db.toInt()} dB"
        tvLevel.setTextColor(
            when {
                db < 50 -> Color.parseColor("#2E7D32")
                db < HIGH_DB -> Color.parseColor("#B26A00")
                else -> Color.parseColor("#C62828")
            }
        )
        findViewById<TextView>(R.id.tvMonitorStatus).text =
            String.format("Monitoring · %d:%02d · hearing %s", seconds / 60, seconds % 60, source)
    }

    private fun checkSustainedAlert() {
        if (recent.size < SUSTAINED_SECONDS) return
        if (recent.average() < HIGH_DB) return

        val now = System.currentTimeMillis()
        if (now - lastAlertAt < ALERT_GAP_MS) return
        lastAlertAt = now

        NotificationHelper.showAlert(
            this,
            "Sustained high noise",
            "The noise around you has stayed above ${HIGH_DB.toInt()} dB for the last " +
                    "$SUSTAINED_SECONDS seconds. Consider moving away or protecting your ears."
        )
    }

    private fun stopMonitoring() {
        running = false
        tick?.let { handler.removeCallbacks(it) }
        releaseRecorder()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        findViewById<Button>(R.id.btnMonitorToggle).text = "Start Monitoring"
        findViewById<TextView>(R.id.tvMonitorStatus).text = "Stopped"
        findViewById<TextView>(R.id.tvMonitorSummary).text = buildSummary()
    }

    private fun buildSummary(): String {
        if (seconds < 5) return "Too short to summarise. Monitor for at least a few seconds."

        val avg = sumDb / seconds
        val highPercent = highSeconds * 100 / seconds
        val sources = sourceSeconds.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key} ${it.value * 100 / seconds}%" }

        val verdict = when {
            highPercent >= 50 -> "Sustained high noise. Staying here a long time can strain your hearing."
            highPercent >= 20 -> "The noise was often high. Take breaks away from this spot."
            else -> "Mostly acceptable. No sustained high noise."
        }

        return String.format(
            "SESSION SUMMARY\nDuration: %d min %d s\nAverage: %.0f dB   Peak: %.0f dB\n" +
                    "Time above %d dB: %d s (%d%%)\nMain sources: %s\n\n%s",
            seconds / 60, seconds % 60, avg, peakDb,
            HIGH_DB.toInt(), highSeconds, highPercent, sources, verdict
        )
    }

    private fun releaseRecorder() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // already stopped
        }
        audioRecord = null
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        tick?.let { handler.removeCallbacks(it) }
        releaseRecorder()
    }
}