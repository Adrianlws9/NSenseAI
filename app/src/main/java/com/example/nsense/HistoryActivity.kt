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
                d                database.noiseDao().getMeasurementsForUser(SessionManager.userId(this@HistoryActivity))
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

    // What the sound classification tells us across all saved readings
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
                "Loudest source on average: %s (%.0f dB)\n\n",
                loudest.key, loudest.value.map { it.noiseLevel }.average()
            )
        )
        sb.append("Average level by source:\n")
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