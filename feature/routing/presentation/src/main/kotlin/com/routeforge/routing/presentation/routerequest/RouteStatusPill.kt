package com.routeforge.routing.presentation.routerequest

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.routeforge.designsystem.components.StatusPill
import com.routeforge.routing.presentation.R

@Composable
internal fun RouteStatusPill(
    state: RouteRequestState,
    modifier: Modifier = Modifier,
) {
    val text =
        state.route?.let { route ->
            stringResource(
                R.string.routing_route_ready_label,
                (state.chosenMode ?: route.mode).localizedLabel(),
                route.distanceMeters.toInt(),
                route.geometry.size,
            )
        } ?: pluralPointsLabel(state.draft.points.size)
    StatusPill(modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun pluralPointsLabel(count: Int): String = stringResource(R.string.routing_points_count_label, count)
