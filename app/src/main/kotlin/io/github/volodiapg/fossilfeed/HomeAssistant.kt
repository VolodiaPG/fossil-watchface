package io.github.volodiapg.fossilfeed

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

/** Reads one value from a Home Assistant REST route, e.g. the time left on a 3D print. */
object HomeAssistant {
    /** What the value found at [Settings.path] means. */
    enum class Unit(val label: String) {
        SECONDS("Seconds left"),
        MINUTES("Minutes left"),
        HOURS("Hours left"),
        END_TIME("End time (ISO 8601)"),
    }

    class Settings(
        val url: String,      // base address, e.g. http://homeassistant.local:8123
        val token: String,    // long-lived access token
        val entity: String,   // e.g. sensor.printer_time_remaining
        val route: String,    // path after the base address; {entity} is replaced
        val path: String,     // where the value sits in the JSON answer, dot separated
        val unit: Unit,
    )

    const val DEFAULT_ROUTE = "/api/states/{entity}"
    const val DEFAULT_PATH = "state"

    private const val PREFS = "homeassistant"
    private const val TIMEOUT_MS = 8000

    fun settings(ctx: Context): Settings? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = p.getString("url", "").orEmpty().trim()
        val entity = p.getString("entity", "").orEmpty().trim()
        if (url.isEmpty() || entity.isEmpty()) return null  // not set up: the feature stays off
        return Settings(
            url, p.getString("token", "").orEmpty().trim(), entity,
            p.getString("route", DEFAULT_ROUTE).orEmpty().ifBlank { DEFAULT_ROUTE },
            p.getString("path", DEFAULT_PATH).orEmpty().ifBlank { DEFAULT_PATH },
            Unit.values().firstOrNull { it.name == p.getString("unit", null) } ?: Unit.MINUTES,
        )
    }

    fun save(ctx: Context, url: String, token: String, entity: String, route: String, path: String, unit: Unit) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", url).putString("token", token).putString("entity", entity)
            .putString("route", route).putString("path", path).putString("unit", unit.name).apply()
    }

    /** The saved fields as typed, for the settings screen. */
    fun raw(ctx: Context, key: String, default: String = ""): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, default).orEmpty()

    /**
     * Unix seconds when the print ends, 0 when nothing is printing (no value, "unknown", nothing left),
     * null when Home Assistant is not set up. Throws when it cannot be reached or answers an error.
     */
    fun printEnd(ctx: Context, nowMs: Long): Long? {
        val s = settings(ctx) ?: return null
        val value = read(s)
        val now = nowMs / 1000
        val end = when (s.unit) {
            Unit.END_TIME -> value?.let(::parseTime)
            Unit.SECONDS -> value?.toDoubleOrNull()?.let { now + it.toLong() }
            Unit.MINUTES -> value?.toDoubleOrNull()?.let { now + (it * 60).toLong() }
            Unit.HOURS -> value?.toDoubleOrNull()?.let { now + (it * 3600).toLong() }
        } ?: return 0L
        return if (end > now) end / 60 * 60 else 0L  // whole minutes, so an unchanged print does not look new every push
    }

    private fun parseTime(text: String): Long? = try {
        OffsetDateTime.parse(text).toEpochSecond()
    } catch (e: Exception) {
        try { Instant.parse(text).epochSecond } catch (e2: Exception) { null }
    }

    private fun read(s: Settings): String? {
        val route = s.route.replace("{entity}", s.entity)
        val conn = URL(s.url.trimEnd('/') + (if (route.startsWith("/")) route else "/$route")).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (s.token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${s.token}")
            if (conn.responseCode !in 200..299) throw java.io.IOException("Home Assistant answered ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            var node: Any? = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
            for (key in s.path.split('.')) {
                val current = node
                node = when (current) {
                    is JSONObject -> current.opt(key)
                    is JSONArray -> key.toIntOrNull()?.let { i -> current.opt(i) }
                    else -> null
                }
            }
            return if (node == null || node == JSONObject.NULL) null else node.toString()
        } finally {
            conn.disconnect()
        }
    }
}
