package com.routeforge.simulation.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Renewable, bounded CPU wake lease, owned only by an actively moving foreground simulation. */
internal class SimulationPowerKeeper(
    private val scope: CoroutineScope,
    private val acquire: (Long) -> Unit,
    private val release: () -> Unit,
) : AutoCloseable {
    private var renewal: Job? = null
    private var closed = false

    fun setActive(active: Boolean) {
        if (closed) return
        if (active && renewal == null) {
            renewal = scope.launch {
                while (isActive) {
                    acquire(10 * 60_000L)
                    delay(5 * 60_000L)
                }
            }
        } else if (!active && renewal != null) {
            renewal?.cancel()
            renewal = null
            release()
        }
    }

    override fun close() {
        setActive(false)
        closed = true
    }
}
