package com.routeforge.simulation.presentation

sealed interface SimulationAction {
    data class OnMapTap(
        val latitude: Double,
        val longitude: Double,
    ) : SimulationAction

    data object OnConfirmTeleport : SimulationAction

    data object OnCancelTeleport : SimulationAction

    data class OnPlaybackSpeedChange(
        val kmh: Float,
    ) : SimulationAction

    data class OnExecutionModeSelected(
        val selection: ExecutionModeSelection,
    ) : SimulationAction

    data class OnExecutionTimesInputChange(
        val value: String,
    ) : SimulationAction

    /** Tapping Play while nothing is running opens the "how many times" dialog — it doesn't start
     *  the route by itself. */
    data object OnStartRouteSimulation : SimulationAction

    data object OnConfirmStartRoute : SimulationAction

    data object OnDismissStartRouteDialog : SimulationAction

    data object OnPauseSimulation : SimulationAction

    data object OnResumeSimulation : SimulationAction

    data object OnStopSimulation : SimulationAction

    /** Tapping the "cancel route" button opens a confirmation sheet — it doesn't clear the route
     *  by itself; confirming it dispatches [OnStopSimulation]. */
    data object OnCancelRouteClick : SimulationAction

    data object OnDismissCancelRoute : SimulationAction

    data object OnSaveRouteClick : SimulationAction

    data class OnRouteNameInputChange(val value: String) : SimulationAction

    data object OnConfirmSaveRoute : SimulationAction

    data object OnDismissSaveRoute : SimulationAction

    data class OnJoystickDrag(
        val bearingDegrees: Float,
    ) : SimulationAction

    data object OnJoystickReleased : SimulationAction

    data object OnToggleJoystick : SimulationAction

    data class OnJoystickSpeedChange(
        val kmh: Float,
    ) : SimulationAction

    data object OnConfirmJoystickInterrupt : SimulationAction

    data object OnDismissJoystickInterrupt : SimulationAction

    data object OnCancelMockClick : SimulationAction

    data object OnConfirmCancelMock : SimulationAction

    data object OnDismissCancelMock : SimulationAction

    data object OnOpenSetupClick : SimulationAction

    data object OnLocationPermissionGranted : SimulationAction

    /** Fired when the screen resumes (app foregrounded, or returning from Settings) — re-checks
     *  mock-location authorization so a revocation is caught even without the user acting on it. */
    data object OnScreenResumed : SimulationAction

    data object OnConfirmFavoriteTeleportCancelRoute : SimulationAction

    data object OnDismissFavoriteTeleportCancelRoute : SimulationAction

    data object OnSwitchRouteModeClick : SimulationAction

    data object OnConfirmSwitchRouteMode : SimulationAction

    data object OnDismissSwitchRouteMode : SimulationAction
}
