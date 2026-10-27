package com.example.nsense

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier

object NoiseClassifier {

    private var classifier: AudioClassifier? = null

    // Ignore any single prediction below this confidence
    private const val MIN_SCORE = 0.1f

    fun initialize(context: Context) {
        if (classifier == null) {
            classifier = AudioClassifier.createFromFile(context, "yamnet.tflite")
        }
    }

    fun createAudioRecord(): AudioRecord? {
        return classifier?.createAudioRecord()
    }

    fun createInputTensorAudio(): TensorAudio? {
        return classifier?.createInputTensorAudio()
    }

    /**
     * Returns one confidence score per app category for this moment
     * (the best score among all labels that belong to that category).
     */
    fun classifyTopScores(tensorAudio: TensorAudio): List<Pair<String, Float>> {
        val localClassifier = classifier ?: return emptyList()
        val results = localClassifier.classify(tensorAudio)

        if (results.isEmpty() || results[0].categories.isEmpty()) {
            return emptyList()
        }

        val sorted = results[0].categories.sortedByDescending { it.score }

        // Shows what the model really hears. Filter Logcat by "NoiseClassifier".
        Log.d(
            "NoiseClassifier",
            "heard: " + sorted.take(5).joinToString { it.label + "=" + "%.2f".format(it.score) }
        )

        val best = mutableMapOf<String, Float>()
        for (item in sorted.take(10)) {
            if (item.score < MIN_SCORE) continue
            val category = simplifyLabel(item.label) ?: continue
            if (item.score > (best[category] ?: 0f)) {
                best[category] = item.score
            }
        }
        return best.map { it.key to it.value }
    }

    // Returns null for labels that are not one of our categories (including vague
    // parent labels such as "Animal" or "Domestic animals, pets").
    private fun simplifyLabel(rawLabel: String): String? {
        // Whole words only, so "cat" no longer matches "Cattle"
        val words = rawLabel.lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.isNotEmpty() }
            .toSet()

        fun has(vararg keywords: String) = keywords.any { it in words }

        return when {
            has("siren", "emergency") -> "Siren"

            has("alarm", "buzzer", "detector") -> "Alarm"

            has("dog", "dogs", "bark", "howl", "cat", "cats", "meow", "purr",
                "bird", "birds", "chirp", "tweet", "insect", "cricket", "mosquito",
                "livestock", "cattle", "horse", "cow", "pig", "sheep", "goat",
                "rooster", "crow", "frog") -> "Animal"

            has("construction", "drill", "hammer", "jackhammer", "tools", "tool",
                "saw", "sawing", "chainsaw", "sanding") -> "Construction"

            has("traffic", "vehicle", "car", "truck", "motorcycle", "bus", "honk",
                "honking", "horn", "toot", "skidding", "tire", "engine", "idling",
                "accelerating", "train", "aircraft", "helicopter", "airplane") -> "Traffic"

            has("speech", "speaking", "conversation", "narration", "monologue",
                "shout", "shouting", "yell", "screaming", "whispering", "babbling",
                "hubbub", "crowd", "laughter") -> "Speech"

            has("music", "musical", "singing", "song", "guitar", "piano", "drum",
                "percussion") -> "Music"

            else -> null
        }
    }
}