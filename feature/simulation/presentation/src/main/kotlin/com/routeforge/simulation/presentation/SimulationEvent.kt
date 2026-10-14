package com.routeforge.simulation.presentation

sealed interface SimulationEvent {
    data object NavigateToSetup : SimulationEvent
    data object RouteSaved : SimulationEvent
}
