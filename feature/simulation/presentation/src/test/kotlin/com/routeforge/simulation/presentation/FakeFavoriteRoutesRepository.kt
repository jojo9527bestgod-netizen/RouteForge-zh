package com.routeforge.simulation.presentation

import com.routeforge.coredomain.FavoriteRoutesRepository
import com.routeforge.coredomain.model.FavoriteRoute
import com.routeforge.coredomain.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeFavoriteRoutesRepository(
    initialFavoriteRoutes: List<FavoriteRoute> = emptyList(),
) : FavoriteRoutesRepository {
    private val _favoriteRoutes = MutableStateFlow(initialFavoriteRoutes)
    private var nextId = 0

    override fun observeFavoriteRoutes(): StateFlow<List<FavoriteRoute>> = _favoriteRoutes

    override fun add(
        name: String,
        points: List<RoutePoint>,
    ): FavoriteRoute {
        val favoriteRoute = FavoriteRoute(id = "fake-${nextId++}", name = name, points = points)
        _favoriteRoutes.value = _favoriteRoutes.value + favoriteRoute
        return favoriteRoute
    }

    override fun rename(
        id: String,
        name: String,
    ) {
        _favoriteRoutes.value =
            _favoriteRoutes.value.map { favoriteRoute ->
                if (favoriteRoute.id == id) favoriteRoute.copy(name = name) else favoriteRoute
            }
    }

    override fun delete(id: String) {
        _favoriteRoutes.value = _favoriteRoutes.value.filterNot { it.id == id }
    }
}
