package com.efootball.companion

import android.app.Activity
import android.app.AlertDialog
import android.media.projection.MediaProjectionManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import android.graphics.Typeface
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import org.json.JSONObject
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
    private lateinit var captureStatus: TextView
    private lateinit var capturePreview: ImageView
    private lateinit var shareCaptureButton: Button
    private lateinit var aiAnalysisButton: Button
    private lateinit var aiSettingsButton: Button
    private lateinit var aiAnalysisResult: TextView
    private lateinit var chatInput: EditText
    private lateinit var chatSendButton: Button
    private lateinit var chatResult: TextView
    private lateinit var voiceButton: Button
    private var speechTts: TextToSpeech? = null
    private var speakNextReply = false
    private val voiceRequestCode = 7402
    private val aiPrefs by lazy { getSharedPreferences("ai_backend", Context.MODE_PRIVATE) }
    private val aiBackendUrl: String get() = aiPrefs.getString("backend_url", "") ?: ""
    private val aiAccessToken: String get() = aiPrefs.getString("access_token", "") ?: ""
    private var latestCapturePath: String? = null
    private val captureRequestCode = 7301
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: "Capture finished."
            val path = intent?.getStringExtra("filePath")
            runOnUiThread {
                captureStatus.text = message
                if (!path.isNullOrBlank()) showCapturedImage(path)
            }
        }
    }

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
        speechTts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val languageResult = speechTts?.setLanguage(Locale("am", "ET")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) speechTts?.language = Locale.getDefault()
            }
        }
        buildScreen()
        val filter = IntentFilter("com.efootball.companion.CAPTURE_RESULT")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(captureReceiver, filter)
        refreshNetworkInfo()
    }

    override fun onDestroy() {
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        speechTts?.stop()
        speechTts?.shutdown()
        super.onDestroy()
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

        section(content, "ONE-SHOT GAME SCREEN CAPTURE")
        val captureInfo = cardText("Capture one screenshot only. After Android permission, switch back to eFootball within 5 seconds. Capture stops immediately after one frame; no continuous recording or background monitoring.")
        content.addView(captureInfo, matchWrap())
        val captureButton = Button(this).apply {
            text = "CAPTURE ONE GAME SCREEN"
            textSize = 13f
            setTextColor(Color.rgb(10, 18, 30))
            backgroundTintList = android.content.res.ColorStateList.valueOf(silver)
            isAllCaps = false
            setOnClickListener { requestOneShotCapture() }
        }
        val cp = matchWrap(); cp.topMargin = dp(8); content.addView(captureButton, cp)
        captureStatus = cardText("No screenshot captured yet.")
        val csp = matchWrap(); csp.topMargin = dp(8); content.addView(captureStatus, csp)
        capturePreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            setBackgroundColor(panel)
        }
        val previewParams = matchWrap(); previewParams.topMargin = dp(8); content.addView(capturePreview, previewParams)
        shareCaptureButton = Button(this).apply {
            text = "SHARE SCREENSHOT"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { shareCapturedImage() }
        }
        val shareParams = matchWrap(); shareParams.topMargin = dp(6); content.addView(shareCaptureButton, shareParams)

        aiSettingsButton = Button(this).apply {
            text = "CONFIGURE REAL AI BACKEND"
            isAllCaps = false
            setOnClickListener { showAiBackendSettings() }
        }
        val aiSettingsParams = matchWrap(); aiSettingsParams.topMargin = dp(8); content.addView(aiSettingsButton, aiSettingsParams)
        aiAnalysisButton = Button(this).apply {
            text = "ANALYZE SCREENSHOT WITH AI"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { analyzeScreenshotWithAi() }
        }
        val aiButtonParams = matchWrap(); aiButtonParams.topMargin = dp(6); content.addView(aiAnalysisButton, aiButtonParams)
        aiAnalysisResult = cardText("Real AI analysis is not configured yet. Set your private backend URL and access token.")
        val aiResultParams = matchWrap(); aiResultParams.topMargin = dp(8); content.addView(aiAnalysisResult, aiResultParams)

        section(content, "AMHARIC AI CHAT COACH")
        chatInput = EditText(this).apply {
            hint = "Ask your eFootball coach in Amharic or English…"
            setTextColor(silver)
            setHintTextColor(muted)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(panel)
            minLines = 2
            maxLines = 4
        }
        content.addView(chatInput, matchWrap())
        chatSendButton = Button(this).apply {
            text = "SEND TO AI COACH"
            isAllCaps = false
            setOnClickListener { sendChatMessage() }
        }
        val chatButtonParams = matchWrap(); chatButtonParams.topMargin = dp(6); content.addView(chatSendButton, chatButtonParams)
        chatResult = cardText("Your AI coach reply will appear here.")
        val chatResultParams = matchWrap(); chatResultParams.topMargin = dp(8); content.addView(chatResult, chatResultParams)
        voiceButton = Button(this).apply {
            text = "TALK TO AI COACH (AMHARIC)"
            isAllCaps = false
            setOnClickListener { startVoiceCoach() }
        }
        val voiceParams = matchWrap(); voiceParams.topMargin = dp(7); content.addView(voiceButton, voiceParams)
        val voiceNote = cardText("Tap to speak, then the AI coach replies in text and voice. This push-to-talk mode avoids an always-on microphone. Amharic speech support depends on your phone's speech services.")
        val voiceNoteParams = matchWrap(); voiceNoteParams.topMargin = dp(6); content.addView(voiceNote, voiceNoteParams)

        section(content, "SMART SCREENSHOT COACH")
        val coach = cardText("Capture one game frame with Android permission, preview it, then choose ANALYZE SCREENSHOT WITH AI. The image is sent only after you tap analyze. No continuous screen recording.")
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
            // Estimate variation within each endpoint, not across different endpoints.
            val endpointJitters = usable.map { it.jitter }.filter { it > 0L }
            val jitter = if (endpointJitters.isEmpty()) 0L else endpointJitters.maxOrNull()!!
            val failureRate = if (totalAttempts == 0) 100 else failures * 100 / totalAttempts
            val grade = when {
                allTimes.isEmpty() || failureRate >= 25 || avg >= redAvgLimit || jitter >= 100 -> "RED"
                avg <= greenAvgLimit && failureRate <= 5 && jitter <= 35 -> "GREEN"
                else -> "YELLOW"
            }
            val advice = when {
                failureRate >= 10 -> "Advice: packet failures are high. Move to a stronger signal or test the other SIM/Wi-Fi."
                jitter >= 60 -> "Advice: connection is fluctuating. Pause downloads, disable hotspot sharing, and retest."
                avg >= 150 -> "Advice: response is slow. Try another network location and compare Wi-Fi with mobile data."
                else -> "Advice: connection looks comparatively steady. Pause downloads and background sync while playing."
            }
            runOnUiThread {
                showTrafficGrade(grade, avg, jitter, failureRate)
                results.text = measurements.joinToString("\n\n") { formatMeasurement(it) } +
                    "\n\nCombined estimate\nAverage TCP response: ${if (avg == Long.MAX_VALUE) "No response" else "$avg ms"}" +
                    "\nWorst endpoint variation: ${jitter} ms\nFailed connection attempts: $failures/$totalAttempts ($failureRate%)" +
                    "\n\n$advice" +
                    "\n\nNote: this is not ICMP packet loss or Konami server ping."
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
        return "${m.label}\nAverage TCP response: $avg  •  Range: $range\nLatency variation (range): ${m.jitter} ms  •  Failed attempts: ${m.failures}/${m.total}"
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
        trafficDetail.text = "TCP response $avgText  •  Variation $jitter ms  •  Failed attempts $loss%"
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


    private fun requestOneShotCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequestCode)
        captureStatus.text = "Approve Android screen-capture permission. Then switch to eFootball within 5 seconds."
    }

    @Deprecated("Deprecated by Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            captureRequestCode -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val service = Intent(this, OneShotCaptureService::class.java).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("projectionData", data)
                    }
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                    captureStatus.text = "Permission granted. Switch to eFootball now; one frame will be captured after 5 seconds."
                } else {
                    captureStatus.text = "Screen capture cancelled. Nothing was recorded."
                }
            }
            voiceRequestCode -> {
                if (resultCode == RESULT_OK) {
                    val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        chatInput.setText(spoken)
                        sendChatMessage()
                    } else {
                        speakNextReply = false
                        chatResult.text = "No speech recognized. Try again or type your question."
                    }
                } else {
                    speakNextReply = false
                    chatResult.text = "Voice input cancelled. You can type your question."
                }
            }
        }
    }

    private fun showCapturedImage(path: String) {
        val file = java.io.File(path)
        if (!file.exists()) {
            captureStatus.text = "Screenshot file was not found. Capture again."
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            captureStatus.text = "Screenshot could not be previewed."
            return
        }
        latestCapturePath = file.absolutePath
        capturePreview.setImageBitmap(bitmap)
        capturePreview.visibility = View.VISIBLE
        shareCaptureButton.isEnabled = true
        captureStatus.text = "Screenshot captured and saved locally. Preview below; share it when ready."
        aiAnalysisButton.isEnabled = aiBackendUrl.isNotBlank() && aiAccessToken.isNotBlank()
        aiAnalysisResult.text = "Screenshot ready. Tap ANALYZE SCREENSHOT WITH AI to request a real visual analysis."
    }

    private fun shareCapturedImage() {
        val path = latestCapturePath ?: return
        val file = java.io.File(path)
        if (!file.exists()) {
            captureStatus.text = "Screenshot file is no longer available. Capture again."
            shareCaptureButton.isEnabled = false
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share eFootball screenshot"))
    }


    private fun normalizeBackendUrl(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank()) return null
        return try {
            val parsed = Uri.parse(value)
            val validScheme = parsed.scheme.equals("https", ignoreCase = true)
            val validHost = !parsed.host.isNullOrBlank()
            val safeAuthority = parsed.userInfo == null && (parsed.port == -1 || parsed.port == 443)
            val simpleBasePath = parsed.path.isNullOrBlank() || parsed.path == "/"
            if (validScheme && validHost && safeAuthority && simpleBasePath &&
                parsed.query == null && parsed.fragment == null) value else null
        } catch (_: Exception) {
            null
        }
    }

    private fun showAiBackendSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val urlInput = EditText(this).apply {
            hint = "https://your-service.onrender.com"
            setSingleLine(true)
            setText(aiBackendUrl)
        }
        val tokenInput = EditText(this).apply {
            hint = "Private app access token"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(aiAccessToken)
        }
        layout.addView(TextView(this).apply { text = "Backend URL"; setTextColor(silver) })
        layout.addView(urlInput)
        layout.addView(TextView(this).apply { text = "App access token"; setTextColor(silver); setPadding(0, dp(10), 0, 0) })
        layout.addView(tokenInput)
        AlertDialog.Builder(this)
            .setTitle("Real AI backend")
            .setMessage("The AI key stays on your server, never inside the APK. Deploy the backend and enter its URL and app token here.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val url = normalizeBackendUrl(urlInput.text.toString())
                val token = tokenInput.text.toString().trim()
                if (url == null) {
                    Toast.makeText(this, "Enter a valid HTTPS backend base URL (for example https://your-service.onrender.com).", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (token.isBlank()) {
                    Toast.makeText(this, "Enter the private app access token.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                aiPrefs.edit().putString("backend_url", url).putString("access_token", token).apply()
                aiAnalysisButton.isEnabled = latestCapturePath != null
                aiAnalysisResult.text = "Secure HTTPS AI backend settings saved. Capture a game screenshot, then analyze it."
                chatResult.text = "Secure AI backend saved. You can now chat with your coach."
            }
            .show()
    }

    private fun analyzeScreenshotWithAi() {
        val path = latestCapturePath
        if (path.isNullOrBlank() || !java.io.File(path).exists()) {
            aiAnalysisResult.text = "Capture a game screenshot first."
            return
        }
        val baseUrl = normalizeBackendUrl(aiBackendUrl) ?: ""
        val token = aiAccessToken
        if (baseUrl.isBlank() || token.isBlank()) {
            aiAnalysisResult.text = "Configure the AI backend URL and access token first."
            return
        }
        aiAnalysisButton.isEnabled = false
        aiAnalysisResult.text = "Sending this screenshot for real AI visual analysis…"
        thread {
            var connection: HttpURLConnection? = null
            try {
                val bytes = java.io.File(path).readBytes()
                val payload = JSONObject()
                    .put("image_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .put("mime_type", "image/jpeg")
                connection = (URL("$baseUrl/analyze").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-App-Token", token)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "No response from AI server."
                if (code !in 200..299) throw IllegalStateException("AI server error $code: $responseText")
                val analysis = JSONObject(responseText).optString("analysis", "")
                if (analysis.isBlank()) throw IllegalStateException("The AI server returned an empty analysis.")
                runOnUiThread { aiAnalysisResult.text = analysis }
            } catch (e: Exception) {
                runOnUiThread { aiAnalysisResult.text = "AI analysis failed: ${e.message ?: "connection error"}. Check backend URL, token, and internet." }
            } finally {
                connection?.disconnect()
                runOnUiThread { aiAnalysisButton.isEnabled = latestCapturePath != null && aiBackendUrl.isNotBlank() && aiAccessToken.isNotBlank() }
            }
        }
    }

    private fun startVoiceCoach() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            chatResult.text = "Speech recognition is unavailable. You can still type to the AI coach."
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "am-ET")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "am-ET")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "በአማርኛ ጥያቄህን ተናገር")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speakNextReply = true
            startActivityForResult(intent, voiceRequestCode)
        } catch (e: Exception) {
            speakNextReply = false
            chatResult.text = "Could not start voice input: ${e.message ?: "speech service unavailable"}"
        }
    }



    private fun sendChatMessage() {
        val message = chatInput.text.toString().trim()
        if (message.isBlank()) {
            speakNextReply = false
            chatResult.text = "እባክህ ጥያቄህን ጻፍ።"
            return
        }
        val baseUrl = aiBackendUrl
        val token = aiAccessToken
        if (baseUrl.isBlank() || token.isBlank()) {
            speakNextReply = false
            chatResult.text = "First configure the AI backend URL and private access token."
            return
        }
        chatSendButton.isEnabled = false
        chatResult.text = "AI Coach is thinking…"
        thread {
            var connection: HttpURLConnection? = null
            try {
                val payload = JSONObject().put("message", message)
                connection = (URL("$baseUrl/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-App-Token", token)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "No response."
                if (code !in 200..299) throw IllegalStateException("AI server error $code: $responseText")
                val answer = JSONObject(responseText).optString("answer", "")
                if (answer.isBlank()) throw IllegalStateException("AI returned an empty answer.")
                runOnUiThread {
                    chatResult.text = answer
                    if (speakNextReply) {
                        speechTts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "coach-reply")
                        speakNextReply = false
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    speakNextReply = false
                    chatResult.text = "Chat failed: ${e.message ?: "connection error"}. Check backend URL, token, and internet."
                }
            } finally {
                connection?.disconnect()
                runOnUiThread { chatSendButton.isEnabled = true }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
