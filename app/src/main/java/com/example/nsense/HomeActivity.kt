package com.example.nsense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Not logged in (for example after logging out): go to the login screen
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.tvTagline).text =
            "Hi ${SessionManager.userName(this)}, understand the sound around you."

        findViewById<Button>(R.id.btnMeasure).setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<Button>(R.id.btnViewMap).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            confirmLogout()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Runs every time Home comes back on screen
    override fun onResume() {
        super.onResume()
        if (!SessionManager.isLoggedIn(this)) return

        showLastReading()

        // Upload any readings that could not be sent earlier
        val userId = SessionManager.userId(this)
        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseSync.syncPending(this@HomeActivity, userId)
        }
    }

    private fun showLastReading() {
        val last = LastReading.load(this) ?: return

        val tvLevel = findViewById<TextView>(R.id.tvNoiseLevel)
        val tvStatus = findViewById<TextView>(R.id.tvNoiseStatus)

        tvLevel.text = "${last.level.toInt()} dB"

        val ago = DateUtils.getRelativeTimeSpanString(
            last.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
        tvStatus.text = "${last.category.uppercase()} · ${last.type} · $ago"

        val color = when (last.category) {
            "Low" -> "#2E7D32"
            "Moderate" -> "#B26A00"
            else -> "#C62828"
        }
        tvLevel.setTextColor(Color.parseColor(color))
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out?")
            .setMessage("You will need to log in again.")
            .setPositiveButton("Log out") { _, _ ->
                SessionManager.logout(this)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        val message = """
            NoiseSense AI measures the sound around you, identifies what is making it, and gives simple advice.

            How it works
            • The microphone records 10 seconds of sound and estimates the noise level in dB.
            • An on-device AI model (YAMNet, trained on Google's AudioSet) identifies the sound sources, such as traffic, speech or sirens.
            • The source changes the result: harsh sounds such as construction or sirens add to the impact rating, and warning sounds trigger alerts.
            • Gemini AI writes advice that fits the source. If you are offline, built-in advice is used.
            • Each reading is saved with its location and shown on the Noise Map.

            Categories (by impact rating)
            • Below 50: Low (quiet)
            • 50 to 70: Moderate
            • Above 70: High (long exposure can harm hearing)

            Note: a phone microphone is not a calibrated sound-level meter, so values are estimates.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("About NoiseSense AI")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
}