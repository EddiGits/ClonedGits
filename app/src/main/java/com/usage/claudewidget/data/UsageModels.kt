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
    /** Explicit display name (e.g. "Fable only" from a limits entry's scope). */
    val labelOverride: String? = null,
) {
    /** "seven_day_fable" → "Fable"; falls back to title-casing the whole key. */
    val label: String get() = labelOverride ?: labelFor(key)

    companion object {
        private val DURATION_WORDS = setOf(
            "five", "seven", "one", "hour", "hours", "day", "days", "week", "weeks",
            "weekly", "daily", "monthly", "session",
        )

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
    /** Raw response body, for the debug screen. Not persisted. */
    val rawBody: String = "",
) {
    companion object {
        fun parse(body: String, now: Long): UsageSnapshot {
            val root = JSONObject(body)
            val keys = root.keys().asSequence().toList()
            // Any other top-level object shaped like a window (e.g. a model-specific
            // weekly limit such as "seven_day_fable") becomes an extra bar.
            val topLevelExtras = keys
                .filter { it != "five_hour" && it != "seven_day" }
                .mapNotNull { key ->
                    val obj = root.optJSONObject(key) ?: return@mapNotNull null
                    if (!obj.isNull("utilization") && !obj.isNull("resets_at")) {
                        LabeledWindow(key, obj.toWindow())
                    } else null
                }
            // Model-specific windows also appear in the "limits" array (kind e.g.
            // "weekly_fable"), often while their top-level key is still null.
            // "session" and "weekly_all" duplicate five_hour/seven_day; skip them.
            val limitExtras = root.optJSONArray("limits")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val kind = o.optString("kind")
                    if (kind.isBlank() || kind == "session" || kind == "weekly_all") {
                        return@mapNotNull null
                    }
                    if (o.isNull("percent") || o.isNull("resets_at")) return@mapNotNull null
                    // A model-scoped limit carries the model in "scope"
                    // (e.g. scope {"model":{"displayName":"Fable"}} → "Fable only").
                    val scope = scopeName(o)
                    LabeledWindow(
                        key = scope ?: kind,
                        window = Window(
                            o.optDouble("percent", 0.0).toFloat(),
                            Iso8601.toEpochMs(o.optString("resets_at", "")),
                        ),
                        labelOverride = scope?.let { "${LabeledWindow.labelFor(it)} only" },
                    )
                }
            }.orEmpty()
            val extras = (topLevelExtras + limitExtras).distinctBy { it.label }
            return UsageSnapshot(
                fiveHour = root.getJSONObject("five_hour").toWindow(),
                sevenDay = root.getJSONObject("seven_day").toWindow(),
                extras = extras,
                fetchedAtEpochMs = now,
                topLevelKeys = keys,
                rawBody = body,
            )
        }

        /** "scope" is a plain string or {"model":{"id","displayName"},"surface":...}. */
        private fun scopeName(o: JSONObject): String? {
            if (o.isNull("scope")) return null
            val sc = o.optJSONObject("scope")
                ?: return o.optString("scope").ifBlank { null }
            sc.optJSONObject("model")?.let { m ->
                for (field in listOf("displayName", "id")) {
                    if (!m.isNull(field)) {
                        m.optString(field).ifBlank { null }?.let { return it }
                    }
                }
            }
            if (!sc.isNull("surface")) {
                sc.optString("surface").ifBlank { null }?.let { return it }
            }
            return null
        }

        private fun JSONObject.toWindow(): Window {
            val util = optDouble("utilization", 0.0).toFloat()
            val reset = optString("resets_at", "")
            return Window(util, Iso8601.toEpochMs(reset))
        }
    }
}
