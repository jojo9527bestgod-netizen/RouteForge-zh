package com.routeforge.routing.presentation

import androidx.navigation.NavController
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.routeforge.routing.presentation.favorites.FavoritesRoot
import com.routeforge.routing.presentation.favorites.FavoritesRoute
import com.routeforge.routing.presentation.regioncatalog.RegionCatalogRoot
import com.routeforge.routing.presentation.regioncatalog.RegionCatalogRoute
import com.routeforge.routing.presentation.regioncoveragemap.RegionCoverageMapRoot
import com.routeforge.routing.presentation.regioncoveragemap.RegionCoverageMapRoute
import com.routeforge.routing.presentation.routerequest.RouteRequestRoot
import com.routeforge.routing.presentation.routerequest.RouteRequestRoute
import com.routeforge.routing.presentation.saved.SavedRoot
import com.routeforge.routing.presentation.saved.SavedRoute
import com.routeforge.routing.presentation.settings.SettingsRoot
import com.routeforge.routing.presentation.settings.SettingsRoute

/** [RegionCatalogRoute]/[RegionCoverageMapRoute] — declared once here and reused by every tab
 *  that can open the catalog ([planRouteGraph], [savedGraph], [settingsGraph], and the Simulate
 *  tab's own graph in the app module). Each caller still gets its own independent destination
 *  instance nested under its own tab's graph, since a saved route, a saved location, or Settings
 *  can all open the catalog from a regions-missing warning or a direct link — nesting it under
 *  whichever tab opened it is what keeps the back button returning to that same tab. */
fun NavGraphBuilder.regionCatalogGraph(navController: NavController) {
    composable<RegionCatalogRoute> {
        RegionCatalogRoot(onOpenCoverageMap = { navController.navigate(RegionCoverageMapRoute) })
    }
    composable<RegionCoverageMapRoute> {
        RegionCoverageMapRoot()
    }
}

/** The Plan Route tab's own nested graph: its root screen plus every sub-screen reachable from it. */
fun NavGraphBuilder.planRouteGraph(
    navController: NavController,
    onGoToSimulate: () -> Unit,
) {
    composable<RouteRequestRoute> {
        RouteRequestRoot(
            onGoToSimulate = onGoToSimulate,
            onOpenRegionCatalog = { navController.navigate(RegionCatalogRoute) },
            onOpenFavorites = { navController.navigate(FavoritesRoute(isPickerMode = true)) },
        )
    }
    regionCatalogGraph(navController)
    composable<FavoritesRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<FavoritesRoute>()
        FavoritesRoot(isPickerMode = route.isPickerMode, onDone = { navController.popBackStack() })
    }
}

/** The Saved tab's own nested graph. See [regionCatalogGraph] for why it's reused rather than
 *  shared across tabs. */
fun NavGraphBuilder.savedGraph(
    navController: NavController,
    onGoToSimulate: () -> Unit,
) {
    composable<SavedRoute> {
        SavedRoot(
            onGoToSimulate = onGoToSimulate,
            onOpenRegionCatalog = { navController.navigate(RegionCatalogRoute) },
        )
    }
    regionCatalogGraph(navController)
}

/** The Settings tab's own nested graph. */
fun NavGraphBuilder.settingsGraph(navController: NavController, additionalSettings: @Composable () -> Unit = {}) {
    composable<SettingsRoute> {
        SettingsRoot(onManageDownloadsClick = { navController.navigate(RegionCatalogRoute) }, additionalSettings = additionalSettings)
    }
    regionCatalogGraph(navController)
}
