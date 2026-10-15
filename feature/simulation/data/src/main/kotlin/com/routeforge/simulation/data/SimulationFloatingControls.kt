package com.routeforge.simulation.data

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.routeforge.simulation.domain.model.SimulationMode
import com.routeforge.simulation.domain.model.SimulationSession
import com.routeforge.simulation.domain.model.SimulationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.abs
import kotlin.math.roundToInt

/** A small, touchable window owned by the existing foreground service, never a second player. */
internal class SimulationFloatingControls(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
    private val onStop: () -> Unit,
    private val onAdjustSpeed: (Int) -> Unit,
) : AutoCloseable {
    private val settings = FloatingControlsPreferences(context)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val repeatScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repeaters = mutableListOf<HoldToRepeat>()
    private var window: LinearLayout? = null
    private var pauseButton: ImageButton? = null
    private var speedRow: LinearLayout? = null
    private var speedLabel: TextView? = null
    private var session: SimulationSession? = null
    private var closed = false
    private var watchingPermission = false
    private var collapsed = settings.preferences.getBoolean("collapsed", false)
    private var cancelDrag: (() -> Unit)? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "RouteForge floating controls"
        alpha = 0.65f
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == FloatingControlsPreferences.KEY_ENABLED) handler.post { refresh() }
    }
    private val permissionListener = AppOpsManager.OnOpChangedListener { op, packageName ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && packageName == context.packageName) {
            handler.post { refresh() }
        }
    }

    init {
        settings.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        try {
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, permissionListener)
            watchingPermission = true
        } catch (error: SecurityException) {
            Log.w(TAG, "Overlay permission watcher unavailable", error)
        }
    }

    fun updateSession(value: SimulationSession?) {
        session = value
        refresh()
    }

    fun onConfigurationChanged() {
        hide()
        refresh()
    }

    private fun refresh() {
        if (closed) return
        val current = session
        if (current == null || !settings.enabled || !Settings.canDrawOverlays(context)) {
            hide()
            return
        }
        if (window == null) show()
        pauseButton?.apply {
            visibility = if (current.mode != SimulationMode.STATIONARY && current.status != SimulationStatus.COMPLETED) {
                View.VISIBLE
            } else {
                View.GONE
            }
            val paused = current.status == SimulationStatus.PAUSED
            setImageResource(if (paused) R.drawable.ic_floating_play else R.drawable.ic_floating_pause)
            contentDescription = context.getString(if (paused) {
                R.string.simulation_notification_action_resume
            } else {
                R.string.simulation_notification_action_pause
            })
        }
        val canAdjustSpeed = current.mode != SimulationMode.STATIONARY && current.status != SimulationStatus.COMPLETED
        speedRow?.visibility = if (canAdjustSpeed) View.VISIBLE else View.GONE
        if (!canAdjustSpeed) repeaters.forEach { it.stop() }
        val speedKmh = (current.speedSetting.metersPerSecond * 3.6f).roundToInt()
        speedLabel?.text = speedKmh.toString()
        speedLabel?.contentDescription = context.getString(R.string.simulation_overlay_speed_value, speedKmh)
        window?.let { panel ->
            panel.post { if (window === panel) clampPosition(panel) }
        }
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        settings.preferences.edit().putBoolean("collapsed", collapsed).apply()
        hide()
        show(restorePosition = false)
        refresh()
    }

    private fun show(restorePosition: Boolean = true) {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(65, 43, 29))
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(8).toFloat()
            contentDescription = context.getString(R.string.simulation_overlay_title)
        }
        val handle = iconButton(
            if (collapsed) R.drawable.ic_floating_expand else R.drawable.ic_floating_collapse,
            if (collapsed) R.string.simulation_overlay_expand else R.string.simulation_overlay_collapse,
            ::toggleCollapsed,
        )
        attachDragHandle(handle)
        if (collapsed) {
            panel.addView(handle, LinearLayout.LayoutParams(dp(56), dp(56)))
        } else {
            val playbackRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            playbackRow.addView(handle, LinearLayout.LayoutParams(dp(44), dp(48)))
            pauseButton = iconButton(R.drawable.ic_floating_pause, R.string.simulation_notification_action_pause) {
                when (session?.status) {
                    SimulationStatus.RUNNING -> onPause()
                    SimulationStatus.PAUSED -> onResume()
                    else -> Unit
                }
            }.also { playbackRow.addView(it, LinearLayout.LayoutParams(dp(72), dp(48))) }
            playbackRow.addView(iconButton(R.drawable.ic_floating_stop, R.string.simulation_notification_action_stop, onStop),
                LinearLayout.LayoutParams(dp(72), dp(48)))
            panel.addView(playbackRow)
            speedRow = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, 0)
                addView(speedButton(R.drawable.ic_floating_minus, R.string.simulation_overlay_speed_decrease, -1),
                    LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    speedLabel = TextView(context).apply {
                        textSize = 20f * CONTROL_SCALE
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setSingleLine()
                    }.also { addView(it) }
                    addView(TextView(context).apply {
                        text = "km/h"
                        textSize = 11f * CONTROL_SCALE
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    })
                }, LinearLayout.LayoutParams(dp(92), dp(48)))
                addView(speedButton(R.drawable.ic_floating_plus, R.string.simulation_overlay_speed_increase, 1),
                    LinearLayout.LayoutParams(dp(48), dp(48)))
            }.also { panel.addView(it) }
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val (maxX, maxY) = movementLimits(panel)
        if (restorePosition) {
            params.x = (settings.preferences.getFloat("x_fraction", 0.05f) * maxX).roundToInt().coerceIn(0, maxX)
            params.y = (settings.preferences.getFloat("y_fraction", 0.2f) * maxY).roundToInt().coerceIn(0, maxY)
        } else {
            params.x = params.x.coerceIn(0, maxX)
            params.y = params.y.coerceIn(0, maxY)
        }
        try {
            windowManager.addView(panel, params)
            window = panel
        } catch (error: SecurityException) {
            Log.w(TAG, "Overlay permission was revoked", error)
            clearControls()
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Overlay window unavailable", error)
            clearControls()
        } catch (error: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "Overlay display unavailable", error)
            clearControls()
        }
    }

    private fun iconButton(icon: Int, label: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        contentDescription = context.getString(label)
        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        minimumWidth = 0
        minimumHeight = 0
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(104, 74, 52))
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { action() }
    }

    @SuppressLint("ClickableViewAccessibility") // performClick preserves a one-step accessibility action.
    private fun speedButton(icon: Int, label: Int, delta: Int): ImageButton {
        val repeater = HoldToRepeat(repeatScope).also { repeaters.add(it) }
        return iconButton(icon, label) { onAdjustSpeed(delta) }.apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        repeater.start { onAdjustSpeed(delta) }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) {
                            view.isPressed = false
                            repeater.stop()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val repeated = repeater.stop()
                        val clicked = view.isPressed && !repeated
                        view.isPressed = false
                        if (clicked) view.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                        view.isPressed = false
                        repeater.stop()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun clampPosition(panel: View) {
        val (maxX, maxY) = movementLimits(panel)
        val x = params.x.coerceIn(0, maxX)
        val y = params.y.coerceIn(0, maxY)
        if (x == params.x && y == params.y) return
        params.x = x
        params.y = y
        try {
            windowManager.updateViewLayout(panel, params)
        } catch (error: SecurityException) {
            hide()
        } catch (error: IllegalArgumentException) {
            hide()
        }
    }

    @SuppressLint("ClickableViewAccessibility") // A short touch uses performClick; a held touch only moves the window.
    private fun attachDragHandle(handle: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var cancelled = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val armDrag = Runnable {
            if (handle.isPressed && !cancelled && window != null) {
                dragging = true
                handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        cancelDrag = {
            handler.removeCallbacks(armDrag)
            handle.isPressed = false
            dragging = false
            cancelled = true
        }
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    cancelled = false
                    view.isPressed = true
                    handler.postDelayed(armDrag, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        cancelled = true
                        handler.removeCallbacks(armDrag)
                    }
                    if (dragging) window?.let { panel ->
                        val (maxX, maxY) = movementLimits(panel)
                        params.x = (startX + dx.roundToInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.roundToInt()).coerceIn(0, maxY)
                        try {
                            windowManager.updateViewLayout(panel, params)
                        } catch (error: SecurityException) {
                            hide()
                        } catch (error: IllegalArgumentException) {
                            hide()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(armDrag)
                    if (dragging) window?.let { panel ->
                        val (maxX, maxY) = movementLimits(panel)
                        settings.preferences.edit()
                            .putFloat("x_fraction", if (maxX == 0) 0f else params.x.toFloat() / maxX)
                            .putFloat("y_fraction", if (maxY == 0) 0f else params.y.toFloat() / maxY)
                            .apply()
                    }
                    view.isPressed = false
                    if (!dragging && !cancelled && event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelDrag?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun movementLimits(panel: View): Pair<Int, Int> {
        val width: Int
        val height: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            width = metrics.bounds.width() - insets.left - insets.right
            height = metrics.bounds.height() - insets.top - insets.bottom
        } else {
            width = context.resources.displayMetrics.widthPixels
            height = context.resources.displayMetrics.heightPixels
        }
        return (width - panel.measuredWidth).coerceAtLeast(0) to (height - panel.measuredHeight).coerceAtLeast(0)
    }

    private fun hide() {
        clearControls()
        val panel = window ?: return
        window = null
        try {
            windowManager.removeView(panel)
        } catch (error: IllegalArgumentException) {
            // The system may have already removed the window when the permission was revoked.
        }
    }

    private fun clearControls() {
        cancelDrag?.invoke()
        cancelDrag = null
        repeaters.forEach { it.stop() }
        repeaters.clear()
        pauseButton = null
        speedRow = null
        speedLabel = null
    }

    override fun close() {
        closed = true
        settings.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (watchingPermission) appOps.stopWatchingMode(permissionListener)
        handler.removeCallbacksAndMessages(null)
        repeatScope.cancel()
        hide()
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density * CONTROL_SCALE).roundToInt()

    private companion object {
        const val TAG = "SimulationFloatingControls"
        const val CONTROL_SCALE = 0.5f
    }
}
