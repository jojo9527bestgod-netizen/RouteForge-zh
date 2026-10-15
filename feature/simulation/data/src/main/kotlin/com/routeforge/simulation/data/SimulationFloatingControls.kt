package com.routeforge.simulation.data

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.routeforge.simulation.domain.model.SimulationMode
import com.routeforge.simulation.domain.model.SimulationSession
import com.routeforge.simulation.domain.model.SimulationStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/** A small, touchable window owned by the existing foreground service, never a second player. */
internal class SimulationFloatingControls(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
    private val onStop: () -> Unit,
) : AutoCloseable {
    private val settings = FloatingControlsPreferences(context)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var window: LinearLayout? = null
    private var pauseButton: Button? = null
    private var session: SimulationSession? = null
    private var closed = false
    private var watchingPermission = false
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "RouteForge floating controls"
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
            setText(if (current.status == SimulationStatus.PAUSED) {
                R.string.simulation_notification_action_resume
            } else {
                R.string.simulation_notification_action_pause
            })
        }
    }

    private fun show() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(65, 43, 29))
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(8).toFloat()
            contentDescription = context.getString(R.string.simulation_overlay_title)
        }
        val handle = TextView(context).apply {
            text = "↕"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = context.getString(R.string.simulation_overlay_drag)
            isClickable = true
        }
        attachDragHandle(handle)
        panel.addView(handle, LinearLayout.LayoutParams(dp(44), dp(48)))
        pauseButton = controlButton(R.string.simulation_notification_action_pause) {
            when (session?.status) {
                SimulationStatus.RUNNING -> onPause()
                SimulationStatus.PAUSED -> onResume()
                else -> Unit
            }
        }.also { panel.addView(it, LinearLayout.LayoutParams(dp(72), dp(48))) }
        panel.addView(controlButton(R.string.simulation_notification_action_stop, onStop),
            LinearLayout.LayoutParams(dp(72), dp(48)))
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val (maxX, maxY) = movementLimits(panel)
        params.x = (settings.preferences.getFloat("x_fraction", 0.05f) * maxX).roundToInt().coerceIn(0, maxX)
        params.y = (settings.preferences.getFloat("y_fraction", 0.2f) * maxY).roundToInt().coerceIn(0, maxY)
        try {
            windowManager.addView(panel, params)
            window = panel
        } catch (error: SecurityException) {
            Log.w(TAG, "Overlay permission was revoked", error)
            pauseButton = null
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Overlay window unavailable", error)
            pauseButton = null
        } catch (error: WindowManager.InvalidDisplayException) {
            Log.w(TAG, "Overlay display unavailable", error)
            pauseButton = null
        }
    }

    private fun controlButton(label: Int, action: () -> Unit) = Button(context).apply {
        setText(label)
        textSize = 14f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(104, 74, 52))
        setOnClickListener { action() }
    }

    @SuppressLint("ClickableViewAccessibility") // Buttons have normal accessibility; only the labelled handle drags.
    private fun attachDragHandle(handle: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragging = true
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
                    window?.let { panel ->
                        val (maxX, maxY) = movementLimits(panel)
                        settings.preferences.edit()
                            .putFloat("x_fraction", if (maxX == 0) 0f else params.x.toFloat() / maxX)
                            .putFloat("y_fraction", if (maxY == 0) 0f else params.y.toFloat() / maxY)
                            .apply()
                    }
                    if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
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
        val panel = window ?: return
        window = null
        pauseButton = null
        try {
            windowManager.removeView(panel)
        } catch (error: IllegalArgumentException) {
            // The system may have already removed the window when the permission was revoked.
        }
    }

    override fun close() {
        closed = true
        settings.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (watchingPermission) appOps.stopWatchingMode(permissionListener)
        handler.removeCallbacksAndMessages(null)
        hide()
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "SimulationFloatingControls"
    }
}
