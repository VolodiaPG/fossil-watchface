package io.github.volodiapg.fossilfeed

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** One screen: grant the calendar permission, push now, see the last result. */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f }
        val grant = Button(this).apply {
            text = "Allow calendar access"
            setOnClickListener { requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 1) }
        }
        val push = Button(this).apply {
            text = "Push to watch now"
            setOnClickListener {
                Thread {
                    Pusher.push(this@MainActivity, force = true)
                    runOnUiThread { refresh() }
                }.start()
            }
        }
        val test = Button(this).apply {
            text = "Send longest-text test event"
            setOnClickListener {
                Thread {
                    val line = Pusher.pushTest(this@MainActivity)
                    runOnUiThread { status.text = line }
                }.start()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(grant)
            addView(push)
            addView(test)
        })
        PushJobService.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun refresh() {
        status.text = buildString {
            appendLine("Calendar access: " + if (Pusher.hasCalendarPermission(this@MainActivity)) "granted" else "missing")
            appendLine("Gadgetbridge: " + if (Pusher.gadgetbridgeInstalled(this@MainActivity)) "found" else "not found")
            append(Pusher.lastStatus(this@MainActivity))
        }
    }
}
