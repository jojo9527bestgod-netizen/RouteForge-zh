package com.routeforge.simulation.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Touch release/cancel must stop the job before the view can disappear or accept another press. */
internal class HoldToRepeat(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var repeated = false

    fun start(action: () -> Unit) {
        stop()
        job = scope.launch {
            delay(450L)
            while (isActive) {
                repeated = true
                action()
                delay(100L)
            }
        }
    }

    /** True means the hold already performed steps, so release must not add a click. */
    fun stop(): Boolean {
        val result = repeated
        job?.cancel()
        job = null
        repeated = false
        return result
    }
}
