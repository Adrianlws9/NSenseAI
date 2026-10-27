package com.example.nsense

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

object RecommendationService {

    private const val MODEL_NAME = "gemini-3.5-flash-lite"

    // Alert rules. They are compared with the impact rating (dB + source penalty).
    const val ALERT_THRESHOLD_DB = 70.0
    const val SIREN_ALERT_MIN_DB = 60.0

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(MODEL_NAME)
    }

    private suspend fun askGemini(prompt: String): String? {
        return try {
            val response = model.generateContent(prompt)
            response.text?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("RecommendationService", "Gemini call failed", e)
            null
        }
    }

    // ---------- Recommendation ----------

    // Returns null if Gemini could not answer, so the screen can label the text honestly
    suspend fun getRecommendation(
        noiseLevel: Double,
        rating: Double,
        noiseType: String,
        category: String,
        sources: String
    ): String? {

        val hint = NoiseSources.tip(noiseType)
        val safety = if (noiseType in NoiseSources.warningSounds) {
            "- This is a warning sound, so mention staying alert."
        } else {
            ""
        }

        val prompt = """
            You are the advice feature of a noise-monitoring app for ordinary people.
            A phone microphone just measured the surroundings. An on-device AI model identified the sound sources.
            - Approximate noise level: ${noiseLevel.toInt()} dB (phone microphones are not calibrated, so treat this as approximate)
            - Main sound source: $noiseType
            - Sources heard (share of the measurement): $sources
            - Impact rating: ${rating.toInt()} (the dB level adjusted for how disturbing this type of sound is)
            - Category: $category (Low is under 50, Moderate is 50 to 70, High is above 70)
            ${if (hint.isNotEmpty()) "- Background on this source: $hint" else ""}
            $safety

            Write practical advice for the person standing there.
            Rules:
            - Maximum 3 short sentences, in very simple everyday English.
            - The advice must fit the main source, not be generic noise advice.
            - Give one concrete action (for example move away, close a window, take a break, limit time here, or use ear protection if it is very loud).
            - Do not give medical diagnoses and do not use scary language.
            - Plain text only: no bullet points, no bold, no emojis.
        """.trimIndent()

        return askGemini(prompt)
    }

    // Offline / failure text: source tip first, then a general line for the category
    fun fallback(category: String, noiseType: String): String {
        val general = when (category) {
            "Low" -> "This place is quiet. It is a good spot to rest or concentrate."
            "Moderate" -> "The noise here is acceptable, but try not to stay for very long periods if it bothers you."
            else -> "This place is very noisy. Move to a quieter area or limit your time here, and protect your ears if you must stay."
        }
        val tip = NoiseSources.tip(noiseType)
        return if (tip.isNotEmpty()) "$tip $general" else general
    }

    // ---------- Alerts ----------

    fun shouldAlert(rating: Double, noiseType: String): Boolean {
        val isWarningSound = noiseType in NoiseSources.warningSounds
        return rating >= ALERT_THRESHOLD_DB ||
                (isWarningSound && rating >= SIREN_ALERT_MIN_DB)
    }

    fun alertTitle(noiseLevel: Double, noiseType: String): String {
        return when (noiseType) {
            "Siren" -> "Siren detected nearby"
            "Alarm" -> "Alarm sound detected"
            else -> "High noise alert: ${noiseLevel.toInt()} dB"
        }
    }

    suspend fun getAlertMessage(noiseLevel: Double, noiseType: String): String {

        val prompt = """
            You write push notification text for a noise-monitoring app.
            Situation: about ${noiseLevel.toInt()} dB (approximate) with the detected sound "$noiseType".

            Write ONE notification message.
            Rules:
            - Maximum 20 words, very simple English.
            - Say what is happening and give one action the person can take now.
            - Calm but clear. No medical claims, no emojis, no quotation marks.
            - Plain text only.
        """.trimIndent()

        return askGemini(prompt) ?: fallbackAlert(noiseType)
    }

    fun fallbackAlert(noiseType: String): String {
        return when (noiseType) {
            "Siren" -> "A siren was detected nearby. Stay aware of your surroundings."
            "Alarm" -> "An alarm sound was detected nearby. Check where it is coming from."
            else -> "This place is very loud. Move to a quieter area or protect your ears."
        }
    }
}