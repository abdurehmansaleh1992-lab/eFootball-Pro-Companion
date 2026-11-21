package com.efootball.companion

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
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
    private lateinit var checkButton: Button
    private lateinit var progress: ProgressBar
    private val navy = Color.rgb(10, 18, 38)
    private val blue = Color.rgb(35, 105, 230)
    private val white = Color.WHITE
    private val muted = Color.rgb(178, 190, 211)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(24, 28, 24, 24)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val title = TextView(this).apply {
            text = "eFOOTBALL PRO\nCOMPANION"
            textSize = 27f
            setTextColor(white)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        content.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "NETWORK INSIGHT  •  MATCH PREPARATION"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 26)
        }
        content.addView(subtitle, matchWrap())

        status = TextView(this).apply {
            text = "Ready to check your connection"
            textSize = 16f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(14, 18, 14, 18)
            setBackgroundColor(Color.rgb(22, 34, 60))
        }
        content.addView(status, matchWrap())

        checkButton = Button(this).apply {
            text = "RUN NETWORK CHECK"
            setTextColor(white)
            backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnClickListener { runNetworkCheck() }
        }
        val buttonParams = matchWrap()
        buttonParams.topMargin = 18
        content.addView(checkButton, buttonParams)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(progress, matchWrap())

        results = TextView(this).apply {
            text = "Results will appear here.\n\nThis check tests public diagnostic endpoints, not Konami's eFootball servers."
            textSize = 14f
            setTextColor(muted)
            setPadding(16, 18, 16, 18)
            setBackgroundColor(Color.rgb(18, 29, 52))
        }
        val resultParams = matchWrap()
        resultParams.topMargin = 20
        content.addView(results, resultParams)

        val coachTitle = TextView(this).apply {
            text = "SMART SCREENSHOT AI COACH"
            textSize = 17f
            setTextColor(white)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 28, 0, 8)
        }
        content.addView(coachTitle, matchWrap())

        val coachInfo = TextView(this).apply {
            text = "Planned feature: analyze screenshots you choose or explicitly share. Screen capture requires Android permission. No silent recording. AI analysis needs a secure AI service."
            textSize = 13f
            setTextColor(muted)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(18, 29, 52))
        }
        content.addView(coachInfo, matchWrap())

        val note = TextView(this).apply {
            text = "\nImportant: public endpoint response times are only connection indicators. They are not actual eFootball match ping. This starter does not change routing or guarantee lag reduction."
            textSize = 12f
            setTextColor(muted)
            setPadding(2, 16, 2, 10)
        }
        content.addView(note, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun runNetworkCheck() {
        checkButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Checking public connection endpoints…"
        results.text = "Running 5 short TCP checks per endpoint."
        thread {
            val report = listOf(
                measureEndpoint("Cloudflare (1.1.1.1)", "1.1.1.1", 443),
                measureEndpoint("Google DNS (8.8.8.8)", "8.8.8.8", 443)
            ).joinToString("\n\n")
            runOnUiThread {
                results.text = report + "\n\nThese are public endpoint checks, not eFootball server ping."
                status.text = "Check complete"
                progress.visibility = View.GONE
                checkButton.isEnabled = true
            }
        }
    }

    private fun measureEndpoint(label: String, host: String, port: Int): String {
        val times = mutableListOf<Long>()
        var failures = 0
        repeat(5) {
            try {
                val start = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
                times.add((System.nanoTime() - start) / 1_000_000)
            } catch (_: Exception) {
                failures++
            }
        }
        if (times.isEmpty()) return "$label\nNo successful connection • failures: $failures/5"
        val avg = times.average().toLong()
        val min = times.minOrNull() ?: 0L
        val max = times.maxOrNull() ?: 0L
        return "$label\nAverage connect time: ${avg} ms\nRange: ${min}–${max} ms\nFailed checks: ${failures}/5"
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
