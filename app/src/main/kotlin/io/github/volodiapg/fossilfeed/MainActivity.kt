package io.github.volodiapg.fossilfeed

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** One screen: grant the calendar permission, set up Home Assistant, push now, see the last result. */
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
        val url = field("Home Assistant address (http://homeassistant.local:8123)", "url", InputType.TYPE_TEXT_VARIATION_URI)
        val token = field("Long-lived access token", "token", InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val entity = field("Entity (sensor.printer_time_remaining)", "entity")
        val route = field("Route, {entity} is replaced", "route", default = HomeAssistant.DEFAULT_ROUTE)
        val path = field("Value in the answer, dot separated (state, attributes.remaining)", "path", default = HomeAssistant.DEFAULT_PATH)
        val statusEntity = field("Status entity, optional (sensor.printer_status)", "statusEntity")
        val statusValues = field("States meaning it is printing, comma separated (printing)", "statusValues")
        val unit = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, HomeAssistant.Unit.values().map { it.label })
            setSelection(HomeAssistant.Unit.values().indexOfFirst { it.name == HomeAssistant.raw(this@MainActivity, "unit") }.coerceAtLeast(0))
        }
        val save = Button(this).apply {
            text = "Save Home Assistant and test"
            setOnClickListener {
                HomeAssistant.save(
                    this@MainActivity, url.text.toString(), token.text.toString(), entity.text.toString(),
                    route.text.toString(), path.text.toString(), HomeAssistant.Unit.values()[unit.selectedItemPosition],
                    statusEntity.text.toString(), statusValues.text.toString(),
                )
                Thread {
                    val line = try {
                        val end = HomeAssistant.printEnd(this@MainActivity, System.currentTimeMillis())
                        when {
                            end == null -> "Home Assistant: address and entity needed"
                            end == 0L -> "Home Assistant: no print running"
                            else -> "Home Assistant: ${(end - System.currentTimeMillis() / 1000) / 60} min left"
                        }
                    } catch (e: Exception) {
                        "Home Assistant: ${e.message ?: e.javaClass.simpleName}"
                    }
                    runOnUiThread { status.text = line }
                }.start()
            }
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(status)
                addView(grant)
                addView(push)
                addView(test)
                addView(TextView(this@MainActivity).apply { text = "\n3D print time left (replaces the distance while printing)"; textSize = 16f })
                listOf(url, token, entity, route, path, unit, statusEntity, statusValues, save).forEach { addView(it) }
                addView(TextView(this@MainActivity).apply {
                    text = "If the test fails with EPERM (operation not permitted), allow Local network access for Fossil Feed in the phone's app settings."
                    textSize = 13f
                })
            })
        })
        PushJobService.schedule(this)
    }

    private fun field(hint: String, key: String, type: Int = InputType.TYPE_CLASS_TEXT, default: String = ""): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or type
            setText(HomeAssistant.raw(this@MainActivity, key, default))
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
