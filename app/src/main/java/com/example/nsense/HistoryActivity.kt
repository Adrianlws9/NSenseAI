package com.example.nsense

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.nsense.data.local.NoiseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_history)

        val database = NoiseDatabase.getDatabase(this)

        val tvHistory =
            findViewById<TextView>(R.id.tvHistory)

        val btnBackHome =
            findViewById<Button>(R.id.btnBackHome)

        CoroutineScope(Dispatchers.IO).launch {

            val measurements =
                database.noiseDao().getAllMeasurements()

            withContext(Dispatchers.Main) {

                if (measurements.isEmpty()) {

                    tvHistory.text =
                        "No measurements saved yet."

                } else {

                    val result = StringBuilder()

                    for (measurement in measurements) {

                        result.append(
                            "Noise Level: ${measurement.noiseLevel} dB\n"
                        )

                        result.append(
                            "Type: ${measurement.noiseType}\n"
                        )

                        result.append(
                            "Category: ${measurement.category}\n\n"
                        )
                    }

                    tvHistory.text = result.toString()
                }
            }
        }

        btnBackHome.setOnClickListener {

            finish()
        }
    }
}