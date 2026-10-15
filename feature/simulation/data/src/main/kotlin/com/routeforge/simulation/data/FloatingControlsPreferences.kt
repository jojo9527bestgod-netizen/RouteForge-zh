package com.routeforge.simulation.data

import android.content.Context

/** Shared by the app settings and the simulation service; disabled until explicitly enabled. */
class FloatingControlsPreferences(context: Context) {
    internal val preferences = context.applicationContext.getSharedPreferences("floating_controls", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_ENABLED, value).apply() }

    internal companion object {
        const val KEY_ENABLED = "enabled"
    }
}
