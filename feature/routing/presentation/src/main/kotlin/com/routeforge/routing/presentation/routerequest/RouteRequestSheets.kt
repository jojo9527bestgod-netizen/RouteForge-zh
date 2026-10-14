package com.routeforge.routing.presentation.routerequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routeforge.coredomain.model.Route
import com.routeforge.coredomain.model.RoutePlaybackMode
import com.routeforge.designsystem.components.NameEntryConfirmationSheet
import com.routeforge.designsystem.theme.Spacing
import com.routeforge.routing.domain.model.RouteFileFormat
import com.routeforge.coredomain.model.RouteOptions
import com.routeforge.routing.presentation.R

private val SwitchModeSpinnerSize = 24.dp

@Composable
internal fun ModeChoiceSheet(
    options: RouteOptions,
    onChoose: (RoutePlaybackMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(Spacing.ScreenContent), verticalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
            Text(stringResource(R.string.routing_choose_mode_label), style = MaterialTheme.typography.titleMedium)
            if (options.guided != null) {
                Button(onClick = { onChoose(RoutePlaybackMode.GUIDED) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.routing_mode_guided_button))
                }
            }
            Button(onClick = { onChoose(RoutePlaybackMode.FREE_ROAM) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.routing_mode_free_roam_button))
            }
        }
    }
}

@Composable
internal fun RouteReadySheet(
    route: Route,
    chosenMode: RoutePlaybackMode?,
    isComputingPreview: Boolean,
    onExport: (RouteFileFormat) -> Unit,
    onUseRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onSwitchMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(Spacing.ScreenContent), verticalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
            Text(
                text =
                    stringResource(
                        R.string.routing_route_ready_label,
                        (chosenMode ?: route.mode).localizedLabel(),
                        route.distanceMeters.toInt(),
                        route.geometry.size,
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
                TextButton(onClick = { onExport(RouteFileFormat.JSON) }) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Text(stringResource(R.string.routing_export_json_button))
                }
                TextButton(onClick = { onExport(RouteFileFormat.GPX) }) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Text(stringResource(R.string.routing_export_gpx_button))
                }
            }
            TextButton(onClick = onSaveRoute, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bookmark, contentDescription = null)
                Text(stringResource(R.string.routing_save_route_button))
            }
            TextButton(onClick = onSwitchMode, modifier = Modifier.fillMaxWidth(), enabled = !isComputingPreview) {
                if (isComputingPreview) {
                    CircularProgressIndicator(modifier = Modifier.size(SwitchModeSpinnerSize))
                } else {
                    Icon(
                        imageVector = if (route.mode == RoutePlaybackMode.GUIDED) Icons.Filled.Straighten else Icons.Filled.Signpost,
                        contentDescription = null,
                    )
                }
                Text(stringResource(if (route.mode == RoutePlaybackMode.GUIDED) R.string.routing_switch_to_free_roam else R.string.routing_switch_to_guided))
            }
            Button(onClick = onUseRoute, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(stringResource(R.string.routing_use_route_button))
            }
        }
    }
}

@Composable
internal fun EditWaypointSheet(
    latitude: String,
    longitude: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(Spacing.ScreenContent), verticalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
            Text(stringResource(R.string.routing_edit_waypoint_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    label = { Text(stringResource(R.string.routing_latitude_label)) },
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    label = { Text(stringResource(R.string.routing_longitude_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
                Button(onClick = onConfirm) { Text(stringResource(R.string.routing_save_button)) }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(stringResource(R.string.routing_delete_button))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routing_cancel_button))
                }
            }
        }
    }
}

@Composable
internal fun SaveRouteSheet(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NameEntryConfirmationSheet(
        title = stringResource(R.string.routing_save_route_title),
        nameInput = nameInput,
        onNameChange = onNameChange,
        nameLabel = stringResource(R.string.routing_route_name_label),
        confirmLabel = stringResource(R.string.routing_save_route_confirm_button),
        dismissLabel = stringResource(R.string.routing_add_waypoint_cancel_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
