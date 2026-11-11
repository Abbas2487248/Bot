
package com.haydaybot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenshotService : Service() {

    companion object {
        const val CHANNEL_ID = "screenshot_channel"
        const val NOTIF_ID = 2
        const val EXTRA_CODE = "result_code"
        const val EXTRA_DATA = "result_data"

        private val _lastBitmap = MutableStateFlow<Bitmap?>(null)
        private val bitmapLock = Any()
        val lastBitmap = _lastBitmap.asStateFlow()

        // Return a private snapshot. The capture thread may replace/recycle its
        // latest frame at any time, so consumers must never receive that bitmap.
        fun getFrame(): Bitmap? = synchronized(bitmapLock) {
            _lastBitmap.value?.let { frame ->
                if (!frame.isRecycled) frame.copy(Bitmap.Config.ARGB_8888, false) else null
            }
        }
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseCaptureResources()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // Start foreground service with the correct type.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val resultCode =
            intent?.getIntExtra(EXTRA_CODE, -1) ?: -1

        val resultData =
            if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_DATA)
            }

        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // MediaProjection must not be initialized more than once per consent token.
        if (projection != null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(it)
            }

            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
            screenDensity = dm.densityDpi

            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            val mediaProjection =
                manager.getMediaProjection(resultCode, resultData)

            projection = mediaProjection

            // Register callback before creating the virtual display.
            mediaProjection.registerCallback(
                projectionCallback,
                mainHandler
            )

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )

                    bitmap.copyPixelsFromBuffer(buffer)

                    val cropped = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        screenWidth,
                        screenHeight
                    )

                    synchronized(bitmapLock) {
                        val oldBitmap = _lastBitmap.value
                        _lastBitmap.value = cropped

                        if (oldBitmap != null && !oldBitmap.isRecycled) {
                            oldBitmap.recycle()
                        }
                    }

                    bitmap.recycle()

                } catch (e: Exception) {
                    android.util.Log.e(
                        "ScreenshotService",
                        "Frame processing failed",
                        e
                    )
                } finally {
                    image.close()
                }

            }, mainHandler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "HayDayBotCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )

            if (virtualDisplay == null) {
                throw IllegalStateException("VirtualDisplay creation failed")
            }

        } catch (e: Exception) {
            android.util.Log.e(
                "ScreenshotService",
                "Screen capture initialization failed",
                e
            )

            releaseCaptureResources()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun releaseCaptureResources() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        projection?.unregisterCallback(projectionCallback)
        projection = null

        _lastBitmap.value = null
    }

    override fun onDestroy() {
        releaseCaptureResources()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screenshot Service",
            NotificationManager.IMPORTANCE_LOW
        )

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hay Day Bot")
            .setContentText("Capturing screen with your permission…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
