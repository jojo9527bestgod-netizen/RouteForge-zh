package com.routeforge.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.routeforge.addresssearch.presentation.AddressSearchRoot
import com.routeforge.addresssearch.presentation.AddressSearchRoute
import com.routeforge.designsystem.theme.RouteForgeTheme
import com.routeforge.mocklocationsetup.domain.usecase.ObserveSetupStateUseCase
import com.routeforge.mocklocationsetup.presentation.MockLocationSetupRoute
import com.routeforge.mocklocationsetup.presentation.mockLocationSetupGraph
import com.routeforge.routing.presentation.planRouteGraph
import com.routeforge.routing.presentation.regionCatalogGraph
import com.routeforge.routing.presentation.regioncatalog.RegionCatalogRoute
import com.routeforge.routing.presentation.routerequest.RouteRequestRoute
import com.routeforge.routing.presentation.saved.SavedRoute
import com.routeforge.routing.presentation.savedGraph
import com.routeforge.routing.presentation.settings.SettingsRoute
import com.routeforge.routing.presentation.settingsGraph
import com.routeforge.simulation.data.EXTRA_OPEN_SIMULATE
import com.routeforge.simulation.presentation.SimulationRoute
import com.routeforge.simulation.presentation.simulationGraph
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val observeSetupState: ObserveSetupStateUseCase by inject()

    /** Set when an intent (initial launch or [onNewIntent]) carries [EXTRA_OPEN_SIMULATE] — e.g. tapping
     *  the simulation notification's body (FR-007) — so the composition can switch to the Simulate tab
     *  even if it was already running on a different one. A plain Activity property, not `remember`ed
     *  inside the composable, since [onNewIntent] fires outside recomposition. */
    private var pendingOpenSimulateRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // Skip the setup flow entirely on launch if it's already done — it should only ever be
        // seen again if something actually revokes mock-location access later (see
        // SimulationViewModel's resume-triggered re-check), never just because the app restarted.
        val startDestination = if (observeSetupState().isReady) SimulateTabRoute else MockLocationSetupRoute
        setContent {
            RouteForgeTheme {
                val navController = rememberNavController()
                LaunchedEffect(pendingOpenSimulateRequest) {
                    if (pendingOpenSimulateRequest) {
                        navController.switchToTab(SimulateTabRoute)
                        pendingOpenSimulateRequest = false
                    }
                }
                MainTabsScaffold(navController = navController) { paddingValues ->
                    val goToSimulate = { navController.switchToTab(SimulateTabRoute) }
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(paddingValues),
                    ) {
                        mockLocationSetupGraph(
                            onSetupReady = {
                                navController.navigate(SimulateTabRoute) {
                                    popUpTo(MockLocationSetupRoute) { inclusive = true }
                                }
                            },
                        )
                        navigation<SimulateTabRoute>(startDestination = SimulationRoute) {
                            simulationGraph(
                                onOpenSetup = { navController.navigate(MockLocationSetupRoute) },
                                onOpenRegionCatalog = { navController.navigate(RegionCatalogRoute) },
                            )
                            regionCatalogGraph(navController)
                        }
                        navigation<PlanTabRoute>(startDestination = RouteRequestRoute) {
                            planRouteGraph(
                                navController = navController,
                                onGoToSimulate = goToSimulate,
                            )
                        }
                        navigation<SavedTabRoute>(startDestination = SavedRoute) {
                            savedGraph(
                                navController = navController,
                                onGoToSimulate = goToSimulate,
                            )
                        }
                        navigation<SettingsTabRoute>(startDestination = SettingsRoute) {
                            settingsGraph(navController = navController, additionalSettings = { FloatingControlsSettingsSection() })
                        }
                        composable<AddressSearchRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<AddressSearchRoute>()
                            AddressSearchRoot(
                                sourceContext = route.sourceContext,
                                referenceLatitude = route.referenceLatitude,
                                referenceLongitude = route.referenceLongitude,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SIMULATE, false) == true) {
            pendingOpenSimulateRequest = true
        }
    }
}
