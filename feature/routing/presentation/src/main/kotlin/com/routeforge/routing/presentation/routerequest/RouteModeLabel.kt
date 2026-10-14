package com.routeforge.routing.presentation.routerequest

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.routing.presentation.R

@Composable
internal fun RoutePlaybackMode.localizedLabel(): String =
    stringResource(
        when (this) {
            RoutePlaybackMode.GUIDED -> R.string.routing_mode_name_guided
            RoutePlaybackMode.FREE_ROAM -> R.string.routing_mode_name_free_roam
        },
    )
