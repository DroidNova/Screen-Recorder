package com.droidnova.screenrecorder.recording.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.droidnova.screenrecorder.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owns the single compact, non-interactive window used before capture starts. */
class RecordingOverlayController(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var countdownView: TextView? = null

    @Suppress("DEPRECATION")
    fun show(value: Int): Boolean = onMainThread {
        if (!Settings.canDrawOverlays(applicationContext) || countdownView != null) return@onMainThread false
        val density = applicationContext.resources.displayMetrics.density
        val size = (108 * density).toInt()
        val view = TextView(applicationContext).apply {
            minWidth = size
            minHeight = size
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 50f)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xD9000000.toInt())
                setStroke((2 * density).toInt().coerceAtLeast(1), ContextCompat.getColor(applicationContext, R.color.recorder_primary_bright))
            }
            updateText(value)
        }
        val manager = applicationContext.getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
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
        } catch (_: RuntimeException) {
            false
        }
    }

    fun update(value: Int?) = runOnMain {
        countdownView?.apply {
            animate().cancel()
            animate().alpha(0.55f).scaleX(0.9f).scaleY(0.9f).setDuration(90).withEndAction {
                updateText(value)
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
        }
    }

    /** Removes the window and waits until the following display frame before returning. */
    fun removeAndAwaitFrame() {
        val latch = CountDownLatch(1)
        mainHandler.post {
            removeOnMain()
            Choreographer.getInstance().postFrameCallback { latch.countDown() }
        }
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun remove() = runOnMain(::removeOnMain)

    private fun removeOnMain() {
        val view = countdownView
        countdownView = null
        view?.animate()?.cancel()
        if (view != null) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: SecurityException) {
            } catch (_: WindowManager.BadTokenException) {
            } catch (_: IllegalArgumentException) {
            } catch (_: RuntimeException) {
            }
        }
        windowManager = null
    }

    private fun TextView.updateText(value: Int?) {
        text = value?.toString() ?: "GO"
        textSize = if (value == null) 42f else 50f
        contentDescription = if (value == null) {
            "Recording started"
        } else {
            "Recording starts in $value ${if (value == 1) "second" else "seconds"}"
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        mainHandler.post {
            result = runCatching(block)
            latch.countDown()
        }
        latch.await()
        return requireNotNull(result).getOrThrow()
    }
}
