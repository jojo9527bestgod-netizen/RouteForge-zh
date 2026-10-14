package com.routeforge.simulation.presentation

import com.routeforge.coredomain.model.MapStyle
import com.routeforge.coredomain.model.RealLocation
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.simulation.domain.model.SimulationSession
import com.routeforge.simulation.domain.model.SpeedPresetType

enum class SimulationErrorType {
    NOT_AUTHORIZED,
    INVALID_SPEED,
    INVALID_EXECUTION_TIMES,
    JOYSTICK_NO_REAL_FIX,
    REAL_LOCATION_PROVIDER_DISABLED,
    REAL_LOCATION_PERMISSION_DENIED,
    REAL_LOCATION_TIMED_OUT,
}

enum class ExecutionModeSelection { ONCE, TIMES, LOOP, PING_PONG }

data class SimulationState(
    val mockedSession: SimulationSession? = null,
    val realLocation: RealLocation? = null,
    val isSearchingRealLocation: Boolean = false,
    val pendingTeleportLatitude: Double? = null,
    val pendingTeleportLongitude: Double? = null,
    val isPendingTeleportBlockedOffline: Boolean = false,
    val isPendingCancelMock: Boolean = false,
    val loadedRoute: Route? = null,
    val routeToSave: Route? = null,
    val routeNameInput: String = "",
    val playbackSpeedKmh: Float = SpeedPresetType.CAR.kilometersPerHour.toFloat(),
    val executionModeSelection: ExecutionModeSelection = ExecutionModeSelection.ONCE,
    val executionTimesInput: String = "2",
    val isStartRouteDialogOpen: Boolean = false,
    val isPendingCancelRoute: Boolean = false,
    val isJoystickVisible: Boolean = false,
    val joystickSpeedKmh: Float = SpeedPresetType.WALKING.kilometersPerHour.toFloat(),
    val isJoystickInterruptPending: Boolean = false,
    val errorType: SimulationErrorType? = null,
    val isBlockedByAuthorization: Boolean = false,
    val pendingFavoriteTeleportTarget: RoutePoint? = null,
    val pendingRouteModeSwitch: RoutePlaybackMode? = null,
    val mapStyle: MapStyle = MapStyle.DEFAULT,
)
