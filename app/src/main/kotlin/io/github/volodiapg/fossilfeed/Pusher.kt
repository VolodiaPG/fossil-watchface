package io.github.volodiapg.fossilfeed

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject

/** Reads the next calendar events and the next alarm, and hands them to Gadgetbridge for the watchface. */
object Pusher {
    private const val GADGETBRIDGE = "nodomain.freeyourgadget.gadgetbridge"
    private const val ACTION_PUSH_CONFIG = "$GADGETBRIDGE.Q_PUSH_CONFIG"
    private const val MAX_EVENTS = 4

    // Text that fits the watch: the location is one line, title and description share up to three lines
    // (about 20 characters a line). Longer text gets "..." in the middle, so both its start and end stay readable.
    private const val MAX_LOCATION = 20
    private const val MAX_TITLE = 26
    private const val MAX_DESC = 28
    private const val RESEND_AFTER_MS = 6 * 60 * 60 * 1000L

    private const val PREFS = "fossilfeed"

    fun hasCalendarPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The JSON the watchface expects; times are Unix seconds, alarm is 0 when none is set. */
    fun payload(ctx: Context, nowMs: Long = System.currentTimeMillis()): String =
        payloadOf(events(ctx, nowMs), nextAlarmSeconds(ctx))

    private fun payloadOf(events: JSONArray, alarmSeconds: Long): String {
        val set = JSONObject()
            .put("widgetEvent._.config.events", events)
            .put("widgetEvent._.config.alarm", alarmSeconds)
        return JSONObject().put("res", JSONObject().put("set", set)).toString()
    }

    /** Sends one event with the longest text the watch face is meant to show, to check the layout on the watch. */
    fun pushTest(ctx: Context): String {
        val start = System.currentTimeMillis() / 1000 + 5 * 60
        val event = event(
            "Quarterly planning review meeting", "Conference room Alpha", "Bring the slides and the laptop charger",
            start, start + 3600
        )
        ctx.sendBroadcast(Intent(ACTION_PUSH_CONFIG).setPackage(GADGETBRIDGE).putExtra("EXTRA_CONFIG_JSON", payloadOf(JSONArray().put(event), 0L)))
        return "Sent the longest-text test event"
    }

    /** Sends the payload unless it is the one already sent recently. Returns a one-line status for the screen. */
    fun push(ctx: Context, force: Boolean): String {
        if (!hasCalendarPermission(ctx)) return "Calendar permission missing"
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val json = payload(ctx, now)
        val unchanged = json == prefs.getString("last", null) && now - prefs.getLong("lastAt", 0) < RESEND_AFTER_MS
        val status = if (unchanged && !force) {
            "Nothing new to send"
        } else {
            ctx.sendBroadcast(Intent(ACTION_PUSH_CONFIG).setPackage(GADGETBRIDGE).putExtra("EXTRA_CONFIG_JSON", json))
            prefs.edit().putString("last", json).putLong("lastAt", now).apply()
            "Sent ${json.length} bytes"
        }
        val line = "${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(now)}: $status"
        prefs.edit().putString("status", line).apply()
        return line
    }

    fun lastStatus(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("status", "Not sent yet") ?: "Not sent yet"

    fun gadgetbridgeInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(GADGETBRIDGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun nextAlarmSeconds(ctx: Context): Long =
        ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.div(1000) ?: 0L

    /** Running or upcoming timed events in the next 24 hours; the watchface keeps only the ones starting today. */
    private fun events(ctx: Context, nowMs: Long): JSONArray {
        val out = JSONArray()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        CalendarContract.Instances.query(ctx.contentResolver, projection, nowMs, nowMs + 24 * 60 * 60 * 1000L)?.use { c ->
            val rows = ArrayList<JSONObject>()
            while (c.moveToNext()) {
                if (c.getInt(5) == 1) continue  // all-day events have no time to show
                rows.add(event(c.getString(0), c.getString(1), c.getString(2), c.getLong(3) / 1000, c.getLong(4) / 1000))
            }
            rows.sortBy { it.getLong("start") }
            rows.take(MAX_EVENTS).forEach { out.put(it) }
        }
        return out
    }

    private fun event(title: String?, location: String?, desc: String?, startSeconds: Long, endSeconds: Long): JSONObject =
        JSONObject()
            .put("title", fit(title, MAX_TITLE))
            .put("location", fit(location, MAX_LOCATION))
            .put("desc", fit(desc, MAX_DESC))
            .put("start", startSeconds)
            .put("end", endSeconds)

    /**
     * Plain ASCII, whitespace collapsed, shortened to [max] characters with "..." in the middle.
     * The watch drops a whole update that contains a non-ASCII character, so accents are removed here.
     */
    internal fun fit(text: String?, max: Int): String {
        val clean = ascii(text ?: "").replace(Regex("\\s+"), " ").trim()
        if (clean.length <= max) return clean
        val keep = max - 3
        val head = (keep + 1) / 2
        return clean.take(head) + "..." + clean.takeLast(keep - head)
    }

    private val replacements = mapOf(
        '\u2018' to "'", '\u2019' to "'", '\u201C' to "\"", '\u201D' to "\"", '\u2013' to "-", '\u2014' to "-",
        '\u2026' to "...", '\u00A0' to " ", '\u0153' to "oe", '\u0152' to "OE", '\u00E6' to "ae", '\u00C6' to "AE",
        '\u00DF' to "ss", '\u20AC' to "EUR",
    )

    private fun ascii(text: String): String {
        val unaccented = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return buildString {
            for (ch in unaccented) {
                when {
                    ch.code < 128 -> append(ch)
                    replacements.containsKey(ch) -> append(replacements[ch])
                    else -> append('?')
                }
            }
        }
    }
}
