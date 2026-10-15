package com.routeforge.simulation.data

import com.routeforge.coredata.ForegroundServiceController
import com.routeforge.coredata.NoOpForegroundServiceController
import com.routeforge.coredomain.GeoMath
import com.routeforge.coredomain.MockLocationAuthorizationChecker
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.holder.LastKnownRealLocationHolder
import com.routeforge.coredomain.model.LatLng
import com.routeforge.coredomain.model.Route
import com.routeforge.simulation.domain.ExecutionModeFailure
import com.routeforge.simulation.domain.JoystickStartFailure
import com.routeforge.simulation.domain.MockLocationPublisher
import com.routeforge.simulation.domain.RouteProgressCalculator
import com.routeforge.simulation.domain.SimulationController
import com.routeforge.simulation.domain.model.ExecutionMode
import com.routeforge.simulation.domain.model.SimulationMode
import com.routeforge.simulation.domain.model.SimulationSession
import com.routeforge.simulation.domain.model.SimulationStatus
import com.routeforge.simulation.domain.model.SpeedSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DEFAULT_TICK_INTERVAL_MILLIS = 100L
private const val DEFAULT_COMPLETION_AUTO_STOP_DELAY_MILLIS = 5_000L

internal class SimulationControllerImpl(
    private val mockLocationPublisher: MockLocationPublisher,
    private val mockLocationAuthorizationChecker: MockLocationAuthorizationChecker,
    private val lastKnownRealLocationHolder: LastKnownRealLocationHolder = LastKnownRealLocationHolder(),
    private val foregroundServiceController: ForegroundServiceController = NoOpForegroundServiceController,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val tickIntervalMillis: Long = DEFAULT_TICK_INTERVAL_MILLIS,
    private val completionAutoStopDelayMillis: Long = DEFAULT_COMPLETION_AUTO_STOP_DELAY_MILLIS,
) : SimulationController {
    private val routeProgressCalculator = RouteProgressCalculator()

    private val _mockedSession = MutableStateFlow<SimulationSession?>(null)
    override val mockedSession: StateFlow<SimulationSession?> = _mockedSession.asStateFlow()

    private val _authorizationLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val authorizationLost: SharedFlow<Unit> = _authorizationLost.asSharedFlow()

    private var tickJob: Job? = null

    override fun teleport(
        latitude: Double,
        longitude: Double,
    ) {
        startSession(
            SimulationSession(
                mode = SimulationMode.STATIONARY,
                status = SimulationStatus.RUNNING,
                latitude = latitude,
                longitude = longitude,
                bearingDegrees = 0f,
                speedMetersPerSecond = 0f,
                route = null,
                distanceTraveledMeters = 0.0,
            ),
        )
    }

    override fun startRoute(
        route: Route,
        speedSetting: SpeedSetting,
        executionMode: ExecutionMode,
    ) {
        val start = route.geometry.firstOrNull() ?: LatLng(0.0, 0.0)
        startSession(
            SimulationSession(
                mode = SimulationMode.ROUTE,
                status = SimulationStatus.RUNNING,
                latitude = start.latitude,
                longitude = start.longitude,
                bearingDegrees = 0f,
                speedMetersPerSecond = speedSetting.metersPerSecond,
                route = route,
                distanceTraveledMeters = 0.0,
                speedSetting = speedSetting,
                executionMode = executionMode,
            ),
        )
    }

    override fun pause() {
        _mockedSession.update { current ->
            if (current?.status == SimulationStatus.RUNNING) current.copy(status = SimulationStatus.PAUSED) else current
        }
    }

    override fun resume() {
        _mockedSession.update { current ->
            if (current?.status == SimulationStatus.PAUSED) current.copy(status = SimulationStatus.RUNNING) else current
        }
    }

    override fun stop() {
        tickJob?.cancel()
        tickJob = null
        _mockedSession.value = null
        mockLocationPublisher.clear()
        foregroundServiceController.stop()
    }

    override fun setSpeed(speed: SpeedSetting) {
        _mockedSession.update { current ->
            current?.copy(speedSetting = speed, speedMetersPerSecond = speed.metersPerSecond)
        }
    }

    override fun setExecutionMode(mode: ExecutionMode): Result<Unit, ExecutionModeFailure> {
        if (mode is ExecutionMode.Times && mode.count <= 0) {
            return Result.Error(ExecutionModeFailure.NonPositiveCount)
        }
        _mockedSession.update { it?.copy(executionMode = mode) }
        return Result.Success(Unit)
    }

    override fun startJoystick(): Result<Unit, JoystickStartFailure> {
        val current = _mockedSession.value
        val latitude: Double
        val longitude: Double
        if (current != null) {
            latitude = current.latitude
            longitude = current.longitude
        } else {
            val lastReal =
                lastKnownRealLocationHolder.location.value
                    ?: return Result.Error(JoystickStartFailure.NoRealLocationFixYet)
            latitude = lastReal.latitude
            longitude = lastReal.longitude
        }

        val speedSetting = current?.speedSetting ?: SpeedSetting.DEFAULT
        startSession(
            SimulationSession(
                mode = SimulationMode.JOYSTICK,
                status = SimulationStatus.RUNNING,
                latitude = latitude,
                longitude = longitude,
                bearingDegrees = current?.bearingDegrees ?: 0f,
                speedMetersPerSecond = speedSetting.metersPerSecond,
                route = null,
                distanceTraveledMeters = 0.0,
                speedSetting = speedSetting,
            ),
        )
        return Result.Success(Unit)
    }

    override fun updateJoystickDirection(bearingDegrees: Float) {
        _mockedSession.update { current ->
            if (current?.mode == SimulationMode.JOYSTICK) current.copy(bearingDegrees = bearingDegrees) else current
        }
    }

    override fun updateActiveRoute(route: Route) {
        _mockedSession.update { current ->
            if (current?.mode == SimulationMode.ROUTE) current.copy(route = route) else current
        }
    }

    private fun startSession(mockedSession: SimulationSession) {
        tickJob?.cancel()
        _mockedSession.value = mockedSession
        publishCurrent()
        foregroundServiceController.start()
        tickJob =
            coroutineScope.launch {
                while (isActive) {
                    delay(tickIntervalMillis)
                    tick(tickIntervalMillis / 1_000.0)
                }
            }
    }

    internal fun tick(elapsedSeconds: Double) {
        if (_mockedSession.value == null) return
        if (!mockLocationAuthorizationChecker.isAuthorized()) {
            stop()
            _authorizationLost.tryEmit(Unit)
            return
        }

        while (true) {
            val current = _mockedSession.value ?: return
            val updated =
                when {
                    current.status == SimulationStatus.RUNNING && current.mode == SimulationMode.ROUTE ->
                        tickRoute(current, elapsedSeconds)
                    current.status == SimulationStatus.RUNNING && current.mode == SimulationMode.JOYSTICK ->
                        tickJoystick(current, elapsedSeconds)
                    else -> current
                }

            // A speed, pause or stop arriving during interpolation must not be overwritten by this tick.
            if (!_mockedSession.compareAndSet(current, updated)) continue
            publishCurrent()
            if (current.status != SimulationStatus.COMPLETED && updated.status == SimulationStatus.COMPLETED) {
                scheduleCompletionAutoStop(updated)
            }
            return
        }
    }

    /** FR-008/009/010/011: a route's truly final completion stays visible (notification + every UI
     *  surface reactive to [mockedSession]) for [completionAutoStopDelayMillis], then ends the session
     *  exactly as [stop] would. Guarding by reference identity (`===`), not just status, means a later,
     *  different session reaching COMPLETED within the same window is unaffected by this stale callback;
     *  [stop] is already idempotent, so a manual stop racing this timer needs no extra guard either way. */
    private fun scheduleCompletionAutoStop(completedSession: SimulationSession) {
        coroutineScope.launch {
            delay(completionAutoStopDelayMillis)
            if (_mockedSession.value === completedSession) stop()
        }
    }

    private fun tickRoute(
        current: SimulationSession,
        elapsedSeconds: Double,
    ): SimulationSession {
        val route = current.route ?: return current
        val newDistance = current.distanceTraveledMeters + current.speedMetersPerSecond * elapsedSeconds
        val effectiveGeometry = if (current.isReversed) route.geometry.reversed() else route.geometry
        val fix = routeProgressCalculator.interpolate(route.copy(geometry = effectiveGeometry), newDistance)

        if (!fix.isRouteExhausted) {
            return current.copy(
                status = SimulationStatus.RUNNING,
                latitude = fix.latitude,
                longitude = fix.longitude,
                bearingDegrees = fix.bearingDegrees,
                distanceTraveledMeters = newDistance,
            )
        }

        val shouldRestart =
            when (val executionMode = current.executionMode) {
                ExecutionMode.Once -> false
                is ExecutionMode.Times -> current.completedRuns + 1 < executionMode.count
                ExecutionMode.Loop -> true
                ExecutionMode.PingPong -> true
            }

        if (!shouldRestart) {
            return current.copy(
                status = SimulationStatus.COMPLETED,
                latitude = fix.latitude,
                longitude = fix.longitude,
                bearingDegrees = fix.bearingDegrees,
                distanceTraveledMeters = newDistance,
            )
        }

        return if (current.executionMode == ExecutionMode.PingPong) {
            // Reverses in place at the endpoint it just reached, rather than teleporting back to
            // route.geometry.first() like Loop/Times do.
            current.copy(
                status = SimulationStatus.RUNNING,
                isReversed = !current.isReversed,
                latitude = fix.latitude,
                longitude = fix.longitude,
                distanceTraveledMeters = 0.0,
                completedRuns = current.completedRuns + 1,
            )
        } else {
            val (startLat, startLon) = route.geometry.first()
            current.copy(
                status = SimulationStatus.RUNNING,
                latitude = startLat,
                longitude = startLon,
                distanceTraveledMeters = 0.0,
                completedRuns = current.completedRuns + 1,
            )
        }
    }

    private fun tickJoystick(
        current: SimulationSession,
        elapsedSeconds: Double,
    ): SimulationSession {
        val distance = current.speedMetersPerSecond * elapsedSeconds
        if (distance <= 0.0) return current
        val (latitude, longitude) = GeoMath.destination(current.latitude, current.longitude, current.bearingDegrees, distance)
        return current.copy(latitude = latitude, longitude = longitude)
    }

    private fun publishCurrent() {
        val current = _mockedSession.value ?: return
        mockLocationPublisher.publish(
            current.latitude,
            current.longitude,
            current.bearingDegrees,
            current.speedMetersPerSecond,
        )
    }
}
