package com.usage.claudewidget.data

import org.json.JSONObject

/** One usage window (5-hour or 7-day). */
data class Window(
    val utilization: Float,      // percent, 0..100
    val resetsAtEpochMs: Long,   // absolute reset time
)

/**
 * A usage window under a top-level key we don't hardcode (e.g. "seven_day_fable").
 * Keeps the raw key so the widget works whatever Anthropic names new windows.
 */
data class LabeledWindow(
    val key: String,
    val window: Window,
) {
    /** "seven_day_fable" → "Fable"; falls back to title-casing the whole key. */
    val label: String get() = labelFor(key)

    companion object {
        private val DURATION_WORDS =
            setOf("five", "seven", "one", "hour", "hours", "day", "days", "week", "weeks")

        fun labelFor(key: String): String {
            val words = key.split('_').filter { it.isNotBlank() }
            val kept = words.filter { it.lowercase() !in DURATION_WORDS }.ifEmpty { words }
            return kept.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
    }
}

/** Parsed snapshot of the /usage endpoint. */
data class UsageSnapshot(
    val fiveHour: Window,
    val sevenDay: Window,
    val extras: List<LabeledWindow>,
    val fetchedAtEpochMs: Long,
    /** All top-level keys of the response, for the debug screen. Not persisted. */
    val topLevelKeys: List<String> = emptyList(),
) {
    companion object {
        fun parse(body: String, now: Long): UsageSnapshot {
            val root = JSONObject(body)
            val keys = root.keys().asSequence().toList()
            // Any other top-level object shaped like a window (e.g. a model-specific
            // weekly limit such as "seven_day_fable") becomes an extra bar.
            val extras = keys
                .filter { it != "five_hour" && it != "seven_day" }
                .mapNotNull { key ->
                    val obj = root.optJSONObject(key) ?: return@mapNotNull null
                    if (obj.has("utilization") && obj.has("resets_at")) {
                        LabeledWindow(key, obj.toWindow())
                    } else null
                }
            return UsageSnapshot(
                fiveHour = root.getJSONObject("five_hour").toWindow(),
                sevenDay = root.getJSONObject("seven_day").toWindow(),
                extras = extras,
                fetchedAtEpochMs = now,
                topLevelKeys = keys,
            )
        }

        private fun JSONObject.toWindow(): Window {
            val util = optDouble("utilization", 0.0).toFloat()
            val reset = optString("resets_at", "")
            return Window(util, Iso8601.toEpochMs(reset))
        }
    }
}
