package com.droidnova.screenrecorder.recording.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.droidnova.screenrecorder.R
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlin.math.roundToInt

class RecordingOverlayController(context: Context) {
    private enum class Mode { Hidden, Countdown, Controls }

    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mode = Mode.Hidden
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var controlsView: FloatingControlsView? = null
    private var controlsSessionId: Long? = null
    private var controlsPaused = false
    private var collapsed = false
    private var reattachAttempted = false
    private var onPause: ((Long) -> Boolean)? = null
    private var onResume: ((Long) -> Boolean)? = null
    private var onStop: ((Long) -> Boolean)? = null

    val isAttached: Boolean get() = rootView?.isAttachedToWindow == true

    fun show(value: Int): Boolean = onMainThread {
        if (mode != Mode.Hidden || rootView != null) return@onMainThread false
        val size = dp(108)
        val view = TextView(applicationContext).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 50f
            typeface = Typeface.DEFAULT_BOLD
            background = overlayBackground(dp(54))
            minWidth = size
            minHeight = size
        }
        updateCountdownView(view, value)
        attach(view, windowParams(touchable = false, centered = true)).also { attached ->
            if (attached) mode = Mode.Countdown
        }
    }

    fun update(value: Int) = mainHandler.post {
        if (mode != Mode.Countdown) return@post
        (rootView as? TextView)?.let { view ->
            updateCountdownView(view, value)
            view.animate().cancel()
            view.alpha = 0.55f
            view.scaleX = 0.86f
            view.scaleY = 0.86f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
        }
    }

    fun showControls(
        sessionId: Long,
        paused: Boolean,
        pause: (Long) -> Boolean,
        resume: (Long) -> Boolean,
        stop: (Long) -> Boolean,
    ): Boolean = onMainThread {
        if (mode != Mode.Hidden || rootView != null || !Settings.canDrawOverlays(applicationContext)) {
            return@onMainThread false
        }
        controlsSessionId = sessionId
        controlsPaused = paused
        collapsed = false
        reattachAttempted = false
        onPause = pause
        onResume = resume
        onStop = stop
        val view = createControlsView()
        val params = windowParams(touchable = true, centered = false)
        setInitialPosition(params, expanded = true)
        attach(view, params).also { attached ->
            if (attached) {
                mode = Mode.Controls
                controlsView = view
            } else {
                clearControlReferences()
            }
        }
    }

    fun updateControls(sessionId: Long, paused: Boolean) = mainHandler.post {
        if (mode != Mode.Controls || controlsSessionId != sessionId) return@post
        if (!Settings.canDrawOverlays(applicationContext)) {
            removeOnMain()
            return@post
        }
        controlsPaused = paused
        controlsView?.setAuthoritativeState(paused)
    }

    fun onDisplayChanged(sessionId: Long, controlsAllowed: Boolean) = mainHandler.post {
        if (mode != Mode.Controls || controlsSessionId != sessionId) return@post
        val view = rootView ?: return@post
        if (view.isAttachedToWindow) {
            updateSizeAndClamp()
        } else if (!reattachAttempted && controlsAllowed && Settings.canDrawOverlays(applicationContext)) {
            reattachAttempted = true
            val params = layoutParams ?: return@post
            if (!attachExisting(view, params)) removeOnMain()
        } else {
            removeOnMain()
        }
    }

    fun removeAndAwaitFrame() {
        val removed = onMainThread { removeOnMain() }
        if (!removed) return
        val frame = CountDownLatch(1)
        mainHandler.post { Choreographer.getInstance().postFrameCallback { frame.countDown() } }
        try { frame.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    fun remove() {
        if (Looper.myLooper() == Looper.getMainLooper()) removeOnMain() else mainHandler.post(::removeOnMain)
    }

    private fun createControlsView() = FloatingControlsView(applicationContext).apply {
        setAuthoritativeState(controlsPaused)
        listener = object : FloatingControlsView.Listener {
            override fun onDrag(deltaX: Int, deltaY: Int) = moveBy(deltaX, deltaY)
            override fun onToggleCollapsed() = toggleCollapsed()
            override fun onMainAction() {
                val id = controlsSessionId ?: return
                val accepted = if (controlsPaused) onResume?.invoke(id) == true else onPause?.invoke(id) == true
                if (!accepted) setActionsEnabled(true)
            }
            override fun onStop() {
                val id = controlsSessionId ?: return
                if (onStop?.invoke(id) != true) setActionsEnabled(true)
            }
        }
    }

    private fun toggleCollapsed() {
        if (mode != Mode.Controls) return
        val params = layoutParams ?: return
        if (collapsed) {
            params.x -= dp(64)
            params.y -= dp(4)
        } else {
            params.x += dp(64)
            params.y += dp(4)
        }
        collapsed = !collapsed
        controlsView?.setCollapsed(collapsed)
        updateSizeAndClamp()
    }

    private fun moveBy(deltaX: Int, deltaY: Int) {
        val params = layoutParams ?: return
        params.x += deltaX
        params.y += deltaY
        clamp(params, rootView ?: return)
        safelyUpdateView()
    }

    private fun updateSizeAndClamp() {
        val view = rootView ?: return
        val params = layoutParams ?: return
        view.measure(
            View.MeasureSpec.makeMeasureSpec(usableBounds().width(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(usableBounds().height(), View.MeasureSpec.AT_MOST),
        )
        clamp(params, view)
        safelyUpdateView()
    }

    private fun safelyUpdateView() {
        val manager = windowManager ?: return
        val view = rootView ?: return
        val params = layoutParams ?: return
        try {
            manager.updateViewLayout(view, params)
        } catch (_: SecurityException) {
            removeOnMain()
        } catch (_: WindowManager.BadTokenException) {
            removeOnMain()
        } catch (_: IllegalArgumentException) {
            removeOnMain()
        }
    }

    private fun attach(view: View, params: WindowManager.LayoutParams): Boolean {
        val attached = attachExisting(view, params)
        if (attached) {
            rootView = view
            layoutParams = params
        }
        return attached
    }

    private fun attachExisting(view: View, params: WindowManager.LayoutParams): Boolean {
        if (view.isAttachedToWindow) return true
        val manager = applicationContext.getSystemService(WindowManager::class.java)
        return try {
            manager.addView(view, params)
            windowManager = manager
            rootView = view
            layoutParams = params
            true
        } catch (_: SecurityException) {
            false
        } catch (_: WindowManager.BadTokenException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun removeOnMain(): Boolean {
        val view = rootView
        view?.animate()?.cancel()
        (view as? FloatingControlsView)?.release()
        if (view != null) try {
            windowManager?.removeViewImmediate(view)
        } catch (_: SecurityException) {
        } catch (_: WindowManager.BadTokenException) {
        } catch (_: IllegalArgumentException) {
        }
        rootView = null
        windowManager = null
        layoutParams = null
        mode = Mode.Hidden
        clearControlReferences()
        return view != null
    }

    private fun clearControlReferences() {
        controlsView?.release()
        controlsView = null
        controlsSessionId = null
        onPause = null
        onResume = null
        onStop = null
        controlsPaused = false
        collapsed = false
        reattachAttempted = false
    }

    private fun windowParams(touchable: Boolean, centered: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = if (centered) Gravity.CENTER else Gravity.TOP or Gravity.START }

    private fun setInitialPosition(params: WindowManager.LayoutParams, expanded: Boolean) {
        val bounds = usableBounds()
        val width = dp(if (expanded) 180 else 52)
        val height = dp(if (expanded) 60 else 52)
        params.x = (bounds.right - width - dp(12)).coerceAtLeast(bounds.left + dp(8))
        params.y = (bounds.top + bounds.height() * 0.58f - height / 2f).roundToInt()
    }

    private fun clamp(params: WindowManager.LayoutParams, view: View) {
        val bounds = usableBounds()
        val margin = dp(8)
        val width = view.measuredWidth.takeIf { it > 0 } ?: dp(if (collapsed) 52 else 180)
        val height = view.measuredHeight.takeIf { it > 0 } ?: dp(if (collapsed) 52 else 60)
        params.x = params.x.coerceIn(bounds.left + margin, (bounds.right - width - margin).coerceAtLeast(bounds.left + margin))
        params.y = params.y.coerceIn(bounds.top + margin, (bounds.bottom - height - margin).coerceAtLeast(bounds.top + margin))
    }

    private fun usableBounds(): Rect {
        val manager = applicationContext.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            return Rect(
                metrics.bounds.left + insets.left,
                metrics.bounds.top + insets.top,
                metrics.bounds.right - insets.right,
                metrics.bounds.bottom - insets.bottom,
            )
        }
        @Suppress("DEPRECATION") val display = manager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") display.getRealMetrics(metrics)
        val statusBar = applicationContext.resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let(applicationContext.resources::getDimensionPixelSize) ?: 0
        val navigationBar = applicationContext.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let(applicationContext.resources::getDimensionPixelSize) ?: 0
        return Rect(0, statusBar, metrics.widthPixels, (metrics.heightPixels - navigationBar).coerceAtLeast(statusBar))
    }

    private fun updateCountdownView(view: TextView, value: Int) {
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

    private fun overlayBackground(radius: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(0xE6000000.toInt())
        setStroke(dp(2), ContextCompat.getColor(applicationContext, R.color.recorder_overlay_green))
    }

    private fun dp(value: Int) = (value * applicationContext.resources.displayMetrics.density).roundToInt()

    private fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        mainHandler.post { result = runCatching(block); latch.countDown() }
        try { latch.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        return requireNotNull(result).getOrThrow()
    }

    private class FloatingControlsView(context: Context) : View(context) {
        interface Listener {
            fun onDrag(deltaX: Int, deltaY: Int)
            fun onToggleCollapsed()
            fun onMainAction()
            fun onStop()
        }

        var listener: Listener? = null
        private val density = resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val path = Path()
        private var paused = false
        private var collapsed = false
        private var actionsEnabled = true
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var movedBeyondSlop = false
        private var pressedRegion = -1

        init {
            isClickable = true
            contentDescription = context.getString(R.string.floating_recording_controls)
        }

        fun setAuthoritativeState(paused: Boolean) {
            this.paused = paused
            actionsEnabled = true
            contentDescription = context.getString(
                if (collapsed) {
                    if (paused) R.string.expand_recording_controls_paused else R.string.expand_recording_controls_recording
                } else R.string.floating_recording_controls,
            )
            invalidate()
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }

        fun setCollapsed(collapsed: Boolean) {
            this.collapsed = collapsed
            setAuthoritativeState(paused)
            requestLayout()
        }

        fun setActionsEnabled(enabled: Boolean) {
            actionsEnabled = enabled
            invalidate()
        }

        fun release() {
            listener = null
            actionsEnabled = false
            cancelPendingInputEvents()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = if (collapsed) 52 else 60
            setMeasuredDimension((if (collapsed) 52 else 180).dp(), size.dp())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.style = Paint.Style.FILL
            paint.color = 0xE6000000.toInt()
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.dp().toFloat()
            paint.color = ContextCompat.getColor(context, R.color.recorder_overlay_green)
            canvas.drawRoundRect(1.dp().toFloat(), 1.dp().toFloat(), width - 1.dp().toFloat(), height - 1.dp().toFloat(), height / 2f, height / 2f, paint)
            if (collapsed) drawCollapsed(canvas) else drawExpanded(canvas)
        }

        private fun drawExpanded(canvas: Canvas) {
            val centers = floatArrayOf(30.dp().toFloat(), 90.dp().toFloat(), 150.dp().toFloat())
            val cy = 30.dp().toFloat()
            paint.strokeWidth = 3.dp().toFloat()
            paint.color = Color.LTGRAY
            for (offset in -1..1) canvas.drawLine((centers[0] - 8.dp()), cy + offset * 5.dp(), centers[0] + 3.dp(), cy + offset * 5.dp(), paint)
            path.reset()
            path.moveTo(centers[0] + 7.dp(), cy - 5.dp())
            path.lineTo(centers[0] + 12.dp(), cy)
            path.lineTo(centers[0] + 7.dp(), cy + 5.dp())
            canvas.drawPath(path, paint)
            paint.color = if (actionsEnabled) Color.WHITE else Color.GRAY
            if (paused) {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(centers[1] - 7.dp(), cy - 10.dp())
                path.lineTo(centers[1] + 10.dp(), cy)
                path.lineTo(centers[1] - 7.dp(), cy + 10.dp())
                path.close()
                canvas.drawPath(path, paint)
            } else {
                paint.strokeWidth = 5.dp().toFloat()
                canvas.drawLine(centers[1] - 5.dp(), cy - 9.dp(), centers[1] - 5.dp(), cy + 9.dp(), paint)
                canvas.drawLine(centers[1] + 5.dp(), cy - 9.dp(), centers[1] + 5.dp(), cy + 9.dp(), paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = if (actionsEnabled) 0xFFFF5252.toInt() else Color.GRAY
            canvas.drawRect(centers[2] - 9.dp(), cy - 9.dp(), centers[2] + 9.dp(), cy + 9.dp(), paint)
        }

        private fun drawCollapsed(canvas: Canvas) {
            paint.style = Paint.Style.FILL
            if (paused) {
                paint.color = Color.WHITE
                canvas.drawRect(20.dp().toFloat(), 18.dp().toFloat(), 24.dp().toFloat(), 34.dp().toFloat(), paint)
                canvas.drawRect(28.dp().toFloat(), 18.dp().toFloat(), 32.dp().toFloat(), 34.dp().toFloat(), paint)
            } else {
                paint.color = 0xFFFF5252.toInt()
                canvas.drawCircle(26.dp().toFloat(), 26.dp().toFloat(), 8.dp().toFloat(), paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                    movedBeyondSlop = false
                    pressedRegion = if (collapsed) 0 else (event.x / 60.dp()).toInt().coerceIn(0, 2)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val draggable = collapsed || pressedRegion == 0
                    movedBeyondSlop = movedBeyondSlop || abs(event.rawX - downRawX) > touchSlop ||
                        abs(event.rawY - downRawY) > touchSlop
                    if (draggable && movedBeyondSlop) {
                        listener?.onDrag((event.rawX - lastRawX).roundToInt(), (event.rawY - lastRawY).roundToInt())
                    }
                    lastRawX = event.rawX; lastRawY = event.rawY
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!movedBeyondSlop) {
                        performClick()
                        when (pressedRegion) {
                            0 -> listener?.onToggleCollapsed()
                            1 -> if (actionsEnabled) { actionsEnabled = false; listener?.onMainAction() }
                            2 -> if (actionsEnabled) { actionsEnabled = false; listener?.onStop() }
                        }
                    }
                    pressedRegion = -1
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { pressedRegion = -1; return true }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = android.widget.Button::class.java.name
            if (collapsed) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            } else {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_COLLAPSE, context.getString(R.string.collapse_recording_controls)))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_MAIN,
                    context.getString(if (paused) R.string.overlay_resume_recording else R.string.overlay_pause_recording),
                ))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_STOP, context.getString(R.string.overlay_stop_recording)))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_MOVE, context.getString(R.string.move_recording_controls)))
            }
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> if (collapsed) {
                    listener?.onToggleCollapsed()
                    return true
                }
                ACTION_COLLAPSE -> { listener?.onToggleCollapsed(); return true }
                ACTION_MAIN -> if (actionsEnabled) {
                    actionsEnabled = false
                    listener?.onMainAction()
                    return true
                }
                ACTION_STOP -> if (actionsEnabled) {
                    actionsEnabled = false
                    listener?.onStop()
                    return true
                }
                ACTION_MOVE -> { listener?.onDrag(-48.dp(), 0); return true }
            }
            return super.performAccessibilityAction(action, arguments)
        }

        private fun Int.dp() = (this * density).roundToInt()

        private companion object {
            const val ACTION_COLLAPSE = 0x01020001
            const val ACTION_MAIN = 0x01020002
            const val ACTION_STOP = 0x01020003
            const val ACTION_MOVE = 0x01020004
        }
    }
}
