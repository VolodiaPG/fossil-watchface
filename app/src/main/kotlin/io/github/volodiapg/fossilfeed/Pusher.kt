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
    private const val RESEND_AFTER_MS = 6 * 60 * 60 * 1000L

    private const val PREFS = "fossilfeed"

    fun hasCalendarPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The JSON the watchface expects; times are Unix seconds, alarm is 0 when none is set. */
    fun payload(ctx: Context, nowMs: Long = System.currentTimeMillis()): String {
        val set = JSONObject()
            .put("widgetEvent._.config.events", events(ctx, nowMs))
            .put("widgetEvent._.config.alarm", nextAlarmSeconds(ctx))
        return JSONObject().put("res", JSONObject().put("set", set)).toString()
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
                rows.add(
                    JSONObject()
                        .put("title", clip(c.getString(0), 60))
                        .put("location", clip(c.getString(1), 60))
                        .put("desc", clip(c.getString(2), 80))
                        .put("start", c.getLong(3) / 1000)
                        .put("end", c.getLong(4) / 1000)
                )
            }
            rows.sortBy { it.getLong("start") }
            rows.take(MAX_EVENTS).forEach { out.put(it) }
        }
        return out
    }

    private fun clip(text: String?, max: Int): String =
        (text ?: "").replace(Regex("\\s+"), " ").trim().take(max)
}
