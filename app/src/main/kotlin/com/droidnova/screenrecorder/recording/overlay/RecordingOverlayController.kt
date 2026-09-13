package com.droidnova.screenrecorder.recording.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.droidnova.screenrecorder.R
import java.util.concurrent.CountDownLatch

class RecordingOverlayController(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var countdownView: TextView? = null

    val isAttached: Boolean get() = countdownView?.isAttachedToWindow == true

    fun show(value: Int): Boolean = onMainThread {
        if (countdownView != null) return@onMainThread isAttached
        val size = dp(108)
        val view = TextView(applicationContext).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 50f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xD9000000.toInt())
                setStroke(dp(2), ContextCompat.getColor(applicationContext, R.color.recorder_overlay_green))
            }
            minWidth = size
            minHeight = size
            layoutParams = android.view.ViewGroup.LayoutParams(size, size)
        }
        val manager = applicationContext.getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        updateView(view, value)
        try {
            manager.addView(view, params)
            windowManager = manager
            countdownView = view
            true
        } catch (_: SecurityException) {
            false
        } catch (_: WindowManager.BadTokenException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun update(value: Int) = mainHandler.post {
        countdownView?.let { view ->
            updateView(view, value)
            view.animate().cancel()
            view.alpha = 0.55f
            view.scaleX = 0.86f
            view.scaleY = 0.86f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
        }
    }

    fun removeAndAwaitFrame() {
        val removed = onMainThread {
            val view = countdownView ?: return@onMainThread false
            view.animate().cancel()
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: SecurityException) {
            } catch (_: WindowManager.BadTokenException) {
            } catch (_: IllegalArgumentException) {
            } finally {
                countdownView = null
                windowManager = null
            }
            true
        }
        if (!removed) return
        val frame = CountDownLatch(1)
        mainHandler.post { Choreographer.getInstance().postFrameCallback { frame.countDown() } }
        try { frame.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    fun remove() {
        if (Looper.myLooper() == Looper.getMainLooper()) removeOnMain() else mainHandler.post(::removeOnMain)
    }

    private fun removeOnMain() {
        val view = countdownView ?: return
        view.animate().cancel()
        try { windowManager?.removeViewImmediate(view) } catch (_: RuntimeException) { }
        countdownView = null
        windowManager = null
    }

    private fun updateView(view: TextView, value: Int) {
        if (value == 0) {
            view.text = "GO"
            view.textSize = 42f
            view.contentDescription = applicationContext.getString(R.string.recording_started_accessibility)
        } else {
            view.text = value.toString()
            view.textSize = 50f
            view.contentDescription = applicationContext.getString(R.string.recording_countdown_accessibility, value)
        }
    }

    private fun dp(value: Int) = (value * applicationContext.resources.displayMetrics.density).toInt()

    private fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        mainHandler.post { result = runCatching(block); latch.countDown() }
        try { latch.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        return requireNotNull(result).getOrThrow()
    }
}
