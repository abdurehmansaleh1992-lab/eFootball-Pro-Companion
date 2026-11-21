package com.efootball.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

class OneShotCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captured = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("eFootball Pro Companion")
            .setContentText("One-time screenshot capture is active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("projectionData", Intent::class.java)
                   else intent?.getParcelableExtra("projectionData")
        if (resultCode == 0 || data == null) {
            finishWithMessage("Capture permission data missing.")
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { releaseCapture() }
            }, handler)
            display = projection?.createVirtualDisplay(
                "EFootballOneShot", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, handler
            )
            // Wait briefly so the player can return to the game; capture exactly one frame.
            handler.postDelayed({ captureSingleFrame() }, 5000L)
        } catch (e: Exception) {
            finishWithMessage("Capture failed: ${e.javaClass.simpleName}")
        }
        return START_NOT_STICKY
    }

    private fun captureSingleFrame() {
        if (captured) return
        val image = try { reader?.acquireLatestImage() } catch (_: Exception) { null }
        if (image == null) {
            finishWithMessage("No frame received. Try again while eFootball is visible.")
            return
        }
        captured = true
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = rowStride / pixelStride
            val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            val file = File(cacheDir, "efootball-capture-${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bitmap.recycle()
            if (cropped !== bitmap) cropped.recycle()
            finishWithMessage("One screenshot saved locally: ${file.name}. No continuous capture is running.")
        } catch (e: Exception) {
            finishWithMessage("Could not save screenshot: ${e.javaClass.simpleName}")
        } finally {
            image.close()
        }
    }

    private fun finishWithMessage(message: String) {
        sendBroadcast(Intent("com.efootball.companion.CAPTURE_RESULT").setPackage(packageName).putExtra("message", message))
        handler.postDelayed({ releaseCapture(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }, 250L)
    }

    private fun releaseCapture() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "One-time screen capture", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseCapture()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "one_shot_capture"
        private const val NOTIFICATION_ID = 7302
    }
}
