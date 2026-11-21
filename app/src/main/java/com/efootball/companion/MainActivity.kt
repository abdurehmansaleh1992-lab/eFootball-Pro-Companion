package com.efootball.companion

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.Button
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
    private lateinit var progress: ProgressBar

    private val bg = Color.rgb(8, 13, 25)
    private val panel = Color.rgb(20, 28, 43)
    private val silver = Color.rgb(220, 226, 235)
    private val muted = Color.rgb(151, 164, 184)
    private val accent = Color.rgb(116, 153, 202)
    private val green = Color.rgb(105, 205, 160)

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

        section(content, "CONNECTION STATUS")
        networkInfo = cardText("Checking network…")
        content.addView(networkInfo, matchWrap())

        section(content, "NETWORK DIAGNOSTICS")
        status = cardText("Ready when you are")
        content.addView(status, matchWrap())

        checkButton = Button(this).apply {
            text = "RUN QUICK CHECK"
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

        progress = ProgressBar(this).apply { visibility = View.GONE }
        val pp = matchWrap()
        pp.topMargin = dp(8)
        content.addView(progress, pp)

        results = cardText("Run a quick check to view connection response times.")
        val rp = matchWrap()
        rp.topMargin = dp(10)
        content.addView(results, rp)

        section(content, "SMART SCREENSHOT COACH")
        val coach = cardText("Screenshot analysis is being prepared. You will choose when to share a game screenshot. No continuous screen recording.")
        content.addView(coach, matchWrap())

        val footer = TextView(this).apply {
            text = "LIGHTWEIGHT DESIGN  •  LOW BACKGROUND ACTIVITY\nPublic endpoint tests are not eFootball server ping. This version does not reroute traffic or guarantee lower lag."
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(20), dp(8), dp(10))
            lineSpacing = dp(3).toFloat()
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
        progress.visibility = View.VISIBLE
        status.text = "Testing connection…"
        results.text = "Please wait. Running a small number of checks."
        thread {
            val report = listOf(
                measureEndpoint("Cloudflare • 1.1.1.1", "1.1.1.1", 443),
                measureEndpoint("Google • 8.8.8.8", "8.8.8.8", 443)
            ).joinToString("\n\n")
            runOnUiThread {
                results.text = report
                status.text = "Quick check completed"
                progress.visibility = View.GONE
                checkButton.isEnabled = true
                refreshNetworkInfo()
            }
        }
    }

    private fun measureEndpoint(label: String, host: String, port: Int): String {
        val times = ArrayList<Long>(3)
        var failures = 0
        repeat(3) {
            try {
                val start = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(host, port), 1400) }
                times.add((System.nanoTime() - start) / 1_000_000)
            } catch (_: Exception) {
                failures++
            }
        }
        if (times.isEmpty()) return "$label\nNo response • failed: $failures/3"
        return "$label\nAverage: ${times.average().toLong()} ms   •   Range: ${times.minOrNull()}–${times.maxOrNull()} ms\nFailed: $failures/3"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
