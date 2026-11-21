package com.efootball.companion

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var networkInfo: TextView
    private lateinit var checkButton: Button
    private lateinit var settingsButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var trafficLight: TextView
    private lateinit var trafficTitle: TextView
    private lateinit var trafficDetail: TextView

    private val bg = Color.rgb(8, 13, 25)
    private val panel = Color.rgb(20, 28, 43)
    private val silver = Color.rgb(220, 226, 235)
    private val muted = Color.rgb(151, 164, 184)
    private val accent = Color.rgb(116, 153, 202)
    private val green = Color.rgb(74, 210, 139)
    private val yellow = Color.rgb(255, 190, 70)
    private val red = Color.rgb(245, 92, 100)
    private val prefs by lazy { getSharedPreferences("network_thresholds", Context.MODE_PRIVATE) }

    private val greenAvgLimit: Int get() = prefs.getInt("green_avg", 90)
    private val redAvgLimit: Int get() = prefs.getInt("red_avg", 220)

    private data class Measurement(
        val label: String,
        val times: List<Long>,
        val failures: Int
    ) {
        val average: Long get() = if (times.isEmpty()) Long.MAX_VALUE else times.average().toLong()
        val jitter: Long get() = if (times.size < 2) 0L else (times.maxOrNull()!! - times.minOrNull()!!)
        val total: Int get() = times.size + failures
        val lossPercent: Int get() = if (total == 0) 100 else failures * 100 / total
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildScreen()
        refreshNetworkInfo()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(22), dp(20), dp(18))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val brand = TextView(this).apply {
            text = "◈"
            textSize = 38f
            setTextColor(silver)
            gravity = Gravity.CENTER
        }
        content.addView(brand, matchWrap())

        val title = TextView(this).apply {
            text = "eFOOTBALL PRO"
            textSize = 25f
            letterSpacing = 0.10f
            setTextColor(silver)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        content.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "COMPANION  •  NETWORK & MATCH COACH"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(24))
        }
        content.addView(subtitle, matchWrap())

        section(content, "SMART NETWORK TRAFFIC LIGHT")
        val lightCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(panel)
        }
        trafficLight = TextView(this).apply {
            text = "●"
            textSize = 38f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        lightCard.addView(trafficLight, LinearLayout.LayoutParams(dp(58), dp(58)))
        val lightText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        trafficTitle = TextView(this).apply {
            text = "NOT TESTED"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(silver)
        }
        trafficDetail = TextView(this).apply {
            text = "Run a test before starting a match."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
        }
        lightText.addView(trafficTitle, matchWrap())
        lightText.addView(trafficDetail, matchWrap())
        lightCard.addView(lightText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(lightCard, matchWrap())

        section(content, "CONNECTION STATUS")
        networkInfo = cardText("Checking network…")
        content.addView(networkInfo, matchWrap())

        section(content, "NETWORK DIAGNOSTICS")
        status = cardText("Ready when you are")
        content.addView(status, matchWrap())

        checkButton = Button(this).apply {
            text = "RUN NETWORK TEST"
            textSize = 14f
            letterSpacing = 0.06f
            setTextColor(Color.rgb(10, 18, 30))
            backgroundTintList = android.content.res.ColorStateList.valueOf(silver)
            isAllCaps = false
            setOnClickListener { runNetworkCheck() }
        }
        val bp = matchWrap()
        bp.topMargin = dp(12)
        content.addView(checkButton, bp)

        settingsButton = Button(this).apply {
            text = "TRAFFIC LIGHT SETTINGS"
            textSize = 12f
            setTextColor(silver)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(54, 68, 88))
            isAllCaps = false
            setOnClickListener { showThresholdSettings() }
        }
        val sp = matchWrap()
        sp.topMargin = dp(6)
        content.addView(settingsButton, sp)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        val pp = matchWrap()
        pp.topMargin = dp(8)
        content.addView(progress, pp)

        results = cardText("The test checks response time, variation (jitter), and failed connections.")
        val rp = matchWrap()
        rp.topMargin = dp(10)
        content.addView(results, rp)

        section(content, "SMART SCREENSHOT COACH")
        val coach = cardText("Screenshot analysis is being prepared. You will choose when to share a game screenshot. No continuous screen recording.")
        content.addView(coach, matchWrap())

        val footer = TextView(this).apply {
            text = "LIGHTWEIGHT DESIGN  •  LOW BACKGROUND ACTIVITY\nTCP checks use public endpoints, not eFootball servers. Traffic-light status is an estimate of connection quality; it cannot detect Konami server load or guarantee lag-free play."
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(20), dp(8), dp(10))
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        content.addView(footer, matchWrap())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun section(parent: LinearLayout, label: String) {
        val t = TextView(this).apply {
            text = label
            textSize = 11f
            letterSpacing = 0.10f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(2), dp(17), 0, dp(8))
        }
        parent.addView(t, matchWrap())
    }

    private fun cardText(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(silver)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(panel)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun refreshNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val kind = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet connected"
            else -> "Network connected"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val metered = cm.isActiveNetworkMetered
        networkInfo.text = "$kind\nInternet validated: ${if (validated) "Yes" else "Not confirmed"}\nData usage: ${if (metered) "Metered" else "Unmetered / unknown"}"
    }

    private fun runNetworkCheck() {
        checkButton.isEnabled = false
        settingsButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Testing network stability…"
        trafficTitle.text = "TESTING…"
        trafficDetail.text = "Measuring response time and connection failures."
        trafficLight.setTextColor(yellow)
        results.text = "Please wait. Checking two public network endpoints."
        thread {
            val measurements = listOf(
                measureEndpoint("Cloudflare • 1.1.1.1", "1.1.1.1", 443),
                measureEndpoint("Google • 8.8.8.8", "8.8.8.8", 443)
            )
            val usable = measurements.filter { it.times.isNotEmpty() }
            val allTimes = usable.flatMap { it.times }
            val totalAttempts = measurements.sumOf { it.total }
            val failures = measurements.sumOf { it.failures }
            val avg = if (allTimes.isEmpty()) Long.MAX_VALUE else allTimes.average().toLong()
            val jitter = if (allTimes.size < 2) 0L else allTimes.maxOrNull()!! - allTimes.minOrNull()!!
            val loss = if (totalAttempts == 0) 100 else failures * 100 / totalAttempts
            val grade = when {
                allTimes.isEmpty() || loss >= 25 || avg >= redAvgLimit || jitter >= 100 -> "RED"
                avg <= greenAvgLimit && loss <= 5 && jitter <= 35 -> "GREEN"
                else -> "YELLOW"
            }
            runOnUiThread {
                showTrafficGrade(grade, avg, jitter, loss)
                results.text = measurements.joinToString("\n\n") { formatMeasurement(it) } +
                    "\n\nCombined estimate\nAverage response: ${if (avg == Long.MAX_VALUE) "No response" else "$avg ms"}" +
                    "\nVariation (jitter): ${jitter} ms\nFailed connections: $failures/$totalAttempts ($loss%)"
                status.text = "Network test completed"
                progress.visibility = View.GONE
                checkButton.isEnabled = true
                settingsButton.isEnabled = true
                refreshNetworkInfo()
            }
        }
    }

    private fun measureEndpoint(label: String, host: String, port: Int): Measurement {
        val times = ArrayList<Long>(5)
        var failures = 0
        repeat(5) {
            try {
                val start = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(host, port), 1600) }
                times.add((System.nanoTime() - start) / 1_000_000)
            } catch (_: Exception) {
                failures++
            }
        }
        return Measurement(label, times, failures)
    }

    private fun formatMeasurement(m: Measurement): String {
        val avg = if (m.times.isEmpty()) "No response" else "${m.average} ms"
        val range = if (m.times.isEmpty()) "—" else "${m.times.minOrNull()}–${m.times.maxOrNull()} ms"
        return "${m.label}\nAverage TCP response: $avg  •  Range: $range\nJitter: ${m.jitter} ms  •  Failed: ${m.failures}/${m.total}"
    }

    private fun showTrafficGrade(grade: String, avg: Long, jitter: Long, loss: Int) {
        val color = when (grade) {
            "GREEN" -> green
            "YELLOW" -> yellow
            else -> red
        }
        trafficLight.setTextColor(color)
        trafficTitle.setTextColor(color)
        trafficTitle.text = when (grade) {
            "GREEN" -> "GREEN • STABLE"
            "YELLOW" -> "YELLOW • CAUTION"
            else -> "RED • BUSY / UNSTABLE"
        }
        val avgText = if (avg == Long.MAX_VALUE) "No response" else "$avg ms"
        trafficDetail.text = "Response $avgText  •  Jitter $jitter ms  •  Loss $loss%"
    }

    private fun showThresholdSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val greenInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(greenAvgLimit.toString())
            hint = "Green average response limit (ms)"
        }
        val redInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(redAvgLimit.toString())
            hint = "Red average response limit (ms)"
        }
        layout.addView(TextView(this).apply {
            text = "GREEN limit: average response up to (ms)"
            setTextColor(silver)
        })
        layout.addView(greenInput)
        layout.addView(TextView(this).apply {
            text = "RED limit: average response from (ms)"
            setTextColor(silver)
            setPadding(0, dp(12), 0, 0)
        })
        layout.addView(redInput)

        AlertDialog.Builder(this)
            .setTitle("Traffic light settings")
            .setMessage("Jitter and packet-failure limits remain automatic. Adjust the response-time thresholds for your connection.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val g = greenInput.text.toString().toIntOrNull() ?: 90
                val r = redInput.text.toString().toIntOrNull() ?: 220
                if (g in 10..500 && r in 20..1000 && r > g) {
                    prefs.edit().putInt("green_avg", g).putInt("red_avg", r).apply()
                    status.text = "Settings saved • Green ≤ $g ms • Red ≥ $r ms"
                } else {
                    status.text = "Invalid settings. Red limit must be higher than green."
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
