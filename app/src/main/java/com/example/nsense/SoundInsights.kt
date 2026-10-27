package com.example.nsense

import android.content.Context

object NoiseSources {

    // Sounds that matter for safety. They take priority over everything else.
    val warningSounds = setOf("Siren", "Alarm")

    // Extra points added to the measured dB, because some sounds are more
    // disturbing than others at the same loudness. App-defined scale; change freely.
    fun penalty(source: String): Int = when (source) {
        "Construction", "Siren", "Alarm" -> 5
        "Traffic", "Music" -> 3
        "Animal" -> 2
        else -> 0
    }

    fun tip(source: String): String = when (source) {
        "Traffic" -> "Road noise is constant. Keep windows closed and avoid waiting close to the road."
        "Construction" -> "Construction noise is usually temporary. Avoid the area during work hours and protect your ears if you are close."
        "Siren" -> "An emergency vehicle is nearby. Stay alert and make way."
        "Alarm" -> "An alarm is sounding nearby. Find out where it is coming from."
        "Speech" -> "Mostly people talking. A quieter corner helps if you need to concentrate."
        "Music" -> "Music is playing nearby. Lower the volume or move away if it disturbs you."
        "Animal" -> "Animal sounds are nearby. This is usually short-lived."
        else -> ""
    }
}

data class LastReadingData(
    val level: Double,
    val category: String,
    val type: String,
    val time: Long
)

// Remembers the most recent reading so the Home screen can show it
object LastReading {
    private const val PREFS = "nsense_prefs"

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun save(context: Context, level: Double, category: String, type: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("lr_level", level.toFloat())
            .putString("lr_category", category)
            .putString("lr_type", type)
            .putLong("lr_time", System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): LastReadingData? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("lr_time")) return null
        return LastReadingData(
            level = p.getFloat("lr_level", 0f).toDouble(),
            category = p.getString("lr_category", "Low") ?: "Low",
            type = p.getString("lr_type", "Other") ?: "Other",
            time = p.getLong("lr_time", 0L)
        )
    }
}