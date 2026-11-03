package com.example.nsense

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nsense.data.local.NoiseDatabase
import com.example.nsense.data.local.NoiseMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private val allSourcesLabel = "All sources"
    private var allMeasurements: List<NoiseMeasurement> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_history)

        val tvInsights = findViewById<TextView>(R.id.tvInsights)
        val tvHistory = findViewById<TextView>(R.id.tvHistory)
        val spinner = findViewById<Spinner>(R.id.spnSource)
        val btnShowMap = findViewById<Button>(R.id.btnShowMap)
        val btnBackHome = findViewById<Button>(R.id.btnBackHome)

        val database = NoiseDatabase.getDatabase(this)

        lifecycleScope.launch {

            val data = withContext(Dispatchers.IO) {
                                database.noiseDao().getMeasurementsForUser(SessionManager.userId(this@HistoryActivity))
            }
            allMeasurements = data

            if (data.isEmpty()) {
                tvInsights.text = "Save a few measurements to see insights here."
                tvHistory.text = "No measurements saved yet."
                return@launch
            }

            tvInsights.text = buildInsights(data)

            // Filter list: "All sources" plus every source found in the data
            val sources = listOf(allSourcesLabel) + data.map { it.noiseType }.distinct().sorted()
            spinner.adapter = ArrayAdapter(
                this@HistoryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                sources
            )
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    showList(tvHistory, sources[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Opens the map, showing only the selected source (or everything)
        btnShowMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            val selected = spinner.selectedItem as? String
            if (selected != null && selected != allSourcesLabel) {
                intent.putExtra("source", selected)
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnShare).setOnClickListener {
            if (allMeasurements.isEmpty()) {
                android.widget.Toast.makeText(
                    this, "No readings to share yet", android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_SUBJECT, "NoiseSense AI noise report")
            send.putExtra(Intent.EXTRA_TEXT, buildReport(allMeasurements))
            startActivity(Intent.createChooser(send, "Share report"))
        }

        btnBackHome.setOnClickListener {
            finish()
        }
    }

    private fun showList(tv: TextView, source: String) {
        val list = if (source == allSourcesLabel) {
            allMeasurements
        } else {
            allMeasurements.filter { it.noiseType == source }
        }

        val format = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
        val sb = StringBuilder()
        for (m in list) {
            sb.append(String.format("%.1f dB · %s · %s\n", m.noiseLevel, m.noiseType, m.category))
            sb.append(format.format(Date(m.timestamp))).append("\n\n")
        }
        tv.text = sb.toString().trim()
    }

    // Plain-text report that can be sent to a warden, landlord or teacher
    private fun buildReport(data: List<NoiseMeasurement>): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
        val newest = data.first()   // list is newest first
        val oldest = data.last()
        val highCount = data.count { it.category == "High" }
        val loudest = data.maxByOrNull { it.noiseLevel }!!

        val sb = StringBuilder()
        sb.append("NoiseSense AI - noise report\n")
        sb.append("Period: ${dateFormat.format(Date(oldest.timestamp))} to ${dateFormat.format(Date(newest.timestamp))}\n")
        sb.append(
            String.format(
                "Readings: %d | High: %d (%d%%) | Average: %.0f dB | Highest: %.0f dB (%s)\n",
                data.size, highCount, highCount * 100 / data.size,
                data.map { it.noiseLevel }.average(), loudest.noiseLevel, loudest.noiseType
            )
        )
        sb.append("Note: values are phone-microphone estimates, not a calibrated meter.\n\n")
        sb.append(buildInsights(data)).append("\n\n")
        sb.append("Latest readings:\n")
        for (m in data.take(10)) {
            sb.append(
                String.format(
                    "%s - %.0f dB, %s, %s\n",
                    dateFormat.format(Date(m.timestamp)), m.noiseLevel, m.noiseType, m.category
                )
            )
        }
        return sb.toString().trim()
    }

    // What the sound classification and the saved readings tell us
    private fun buildInsights(data: List<NoiseMeasurement>): String {
        val bySource = data.groupBy { it.noiseType }

        val mostCommon = bySource.maxByOrNull { it.value.size }!!
        val loudest = bySource.maxByOrNull { e -> e.value.map { it.noiseLevel }.average() }!!
        val highCount = data.count { it.category == "High" }

        val sb = StringBuilder()
        sb.append("INSIGHTS\n")
        sb.append("${data.size} readings, $highCount of them High\n")
        sb.append("Most common source: ${mostCommon.key} (${mostCommon.value.size} of ${data.size})\n")
        sb.append(
            String.format(
                "Loudest source on average: %s (%.0f dB)\n",
                loudest.key, loudest.value.map { it.noiseLevel }.average()
            )
        )

        // WHEN is it noisiest? (needs a few readings to mean anything)
        if (data.size >= 3) {
            val cal = java.util.Calendar.getInstance()
            val byHour = data.groupBy {
                cal.timeInMillis = it.timestamp
                cal.get(java.util.Calendar.HOUR_OF_DAY)
            }
            val worst = byHour.maxByOrNull { e -> e.value.map { it.noiseLevel }.average() }!!
            val h = worst.key
            val h12 = if (h % 12 == 0) 12 else h % 12
            val ampm = if (h < 12) "AM" else "PM"
            sb.append(
                String.format(
                    "Noisiest time of day: around %d %s (%.0f dB average)\n",
                    h12, ampm, worst.value.map { it.noiseLevel }.average()
                )
            )
        }

        // WHERE is it noisiest? Readings within about 100 m count as one spot.
        val bySpot = data
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .groupBy { String.format("%.3f, %.3f", it.latitude, it.longitude) }
        if (bySpot.size >= 2) {
            val noisy = bySpot.maxByOrNull { e -> e.value.map { it.noiseLevel }.average() }!!
            val quiet = bySpot.minByOrNull { e -> e.value.map { it.noiseLevel }.average() }!!
            sb.append(
                String.format(
                    "Noisiest spot: %s (%.0f dB, %d readings)\n",
                    noisy.key, noisy.value.map { it.noiseLevel }.average(), noisy.value.size
                )
            )
            sb.append(
                String.format(
                    "Quietest spot: %s (%.0f dB, %d readings)\n",
                    quiet.key, quiet.value.map { it.noiseLevel }.average(), quiet.value.size
                )
            )
        }

        sb.append("\nAverage level by source:\n")
        for ((source, list) in bySource.entries.sortedByDescending {
                e -> e.value.map { it.noiseLevel }.average()
        }) {
            sb.append(
                String.format(
                    "• %s: %.0f dB (%d readings)\n",
                    source, list.map { it.noiseLevel }.average(), list.size
                )
            )
        }
        return sb.toString().trim()
    }
}