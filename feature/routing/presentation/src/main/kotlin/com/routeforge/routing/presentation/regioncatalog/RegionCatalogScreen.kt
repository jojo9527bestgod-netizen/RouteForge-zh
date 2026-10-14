package com.routeforge.routing.presentation.regioncatalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routeforge.designsystem.components.ConfirmationBottomSheet
import com.routeforge.designsystem.components.IconBadge
import com.routeforge.designsystem.components.TopBanner
import com.routeforge.designsystem.theme.RouteForgeTheme
import com.routeforge.designsystem.theme.Spacing
import com.routeforge.coredomain.model.Region
import com.routeforge.coredomain.model.RegionStatus
import com.routeforge.routing.domain.model.RegionUsage
import com.routeforge.routing.presentation.R
import org.koin.androidx.compose.koinViewModel

private val CardSpacing = 12.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardPadding = 20.dp
private val EmptyStateIconBadgeSize = 72.dp

@Composable
fun RegionCatalogRoot(
    viewModel: RegionCatalogViewModel = koinViewModel(),
    onOpenCoverageMap: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var downloadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) { is RegionCatalogEvent.DownloadFailed -> downloadFailed = true }
        }
    }
    RegionCatalogScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenCoverageMap = onOpenCoverageMap,
        downloadErrorMessage = if (downloadFailed) stringResource(R.string.region_catalog_download_failed) else null,
    )
}

@Composable
fun RegionCatalogScreen(
    state: RegionCatalogState,
    onAction: (RegionCatalogAction) -> Unit,
    onOpenCoverageMap: () -> Unit = {},
    downloadErrorMessage: String? = null,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (state.regions.isEmpty()) {
                EmptyRegionsContent(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.ScreenContent),
                    contentPadding = PaddingValues(horizontal = Spacing.ScreenContent),
                    verticalArrangement = Arrangement.spacedBy(CardSpacing),
                ) {
                    items(state.regions, key = { it.id }) { region ->
                        RegionCard(
                            region = region,
                            isNeeded = region.id in state.neededRegionIds,
                            isDownloading = state.downloadingRegionId == region.id,
                            downloadProgress = state.downloadProgress,
                            isDownloadButtonEnabled = state.downloadingRegionId == null,
                            onDownloadClick = { onAction(RegionCatalogAction.OnDownloadRegion(region.id)) },
                            onCancelDownloadClick = { onAction(RegionCatalogAction.OnCancelDownloadClick) },
                            onDeleteClick = { onAction(RegionCatalogAction.OnDeleteRegionClick(region.id)) },
                        )
                    }
                }
            }
            downloadErrorMessage?.let { message ->
                TopBanner(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.ScreenContent, vertical = 8.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onOpenCoverageMap, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.region_catalog_view_coverage_map_button))
            }
            Text(
                text = stringResource(R.string.region_catalog_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(Spacing.ScreenContent),
            )
        }
    }

    if (state.pendingCancelDownload) {
        CancelDownloadConfirmationSheet(
            onConfirm = { onAction(RegionCatalogAction.OnConfirmCancelDownload) },
            onDismiss = { onAction(RegionCatalogAction.OnDismissCancelDownload) },
        )
    }

    val pendingDeleteRegion = state.pendingDeleteRegion
    if (pendingDeleteRegion != null) {
        DeleteRegionConfirmationSheet(
            usage = state.deleteRegionUsage,
            onConfirm = { onAction(RegionCatalogAction.OnConfirmDeleteRegion) },
            onDismiss = { onAction(RegionCatalogAction.OnDismissDeleteRegion) },
        )
    }
}

@Composable
private fun CancelDownloadConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationBottomSheet(title = stringResource(R.string.region_catalog_cancel_download_confirm_title), onDismiss = onDismiss) {
        Text(stringResource(R.string.region_catalog_cancel_download_confirm_message))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
            Button(onClick = onConfirm) { Text(stringResource(R.string.region_catalog_cancel_download_confirm_button)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.region_catalog_cancel_download_dismiss_button)) }
        }
    }
}

@Composable
private fun DeleteRegionConfirmationSheet(
    usage: RegionUsage?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationBottomSheet(title = stringResource(R.string.region_catalog_delete_confirm_title), onDismiss = onDismiss) {
        Text(stringResource(R.string.region_catalog_delete_confirm_message))
        if (usage != null && usage.isInUse) {
            val usedBy =
                buildList {
                    if (usage.neededByDraft) add(stringResource(R.string.region_catalog_delete_warning_item_draft))
                    if (usage.neededByActiveRoute) add(stringResource(R.string.region_catalog_delete_warning_item_active_route))
                    addAll(usage.neededBySavedRouteNames)
                }
            Text(
                text = stringResource(R.string.region_catalog_delete_warning_message, usedBy.joinToString(", ")),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
            Button(onClick = onConfirm) { Text(stringResource(R.string.region_catalog_delete_confirm_button)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.region_catalog_delete_dismiss_button)) }
        }
    }
}

@Composable
private fun EmptyRegionsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(icon = Icons.Filled.CloudOff, size = EmptyStateIconBadgeSize)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.region_catalog_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.region_catalog_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RegionCard(
    region: Region,
    isNeeded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    isDownloadButtonEnabled: Boolean,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isNeeded) {
                        Text(
                            text = stringResource(R.string.region_catalog_needed_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = region.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = region.status.toIcon(),
                            contentDescription = null,
                            tint = region.status.toColor(),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${region.sizeDisplayText()} • ${region.status.toDisplayText()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = region.status.toColor(),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ControlsRow)) {
                    if (region.status == RegionStatus.NOT_DOWNLOADED || region.status == RegionStatus.PARTIALLY_DOWNLOADED) {
                        Button(onClick = onDownloadClick, enabled = isDownloadButtonEnabled) {
                            Text(stringResource(R.string.region_catalog_download_button))
                        }
                    }
                    if (region.status == RegionStatus.DOWNLOADED || region.status == RegionStatus.PARTIALLY_DOWNLOADED) {
                        TextButton(onClick = onDeleteClick) {
                            Text(stringResource(R.string.region_catalog_delete_button))
                        }
                    }
                }
            }
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                if (downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                }
                TextButton(onClick = onCancelDownloadClick) {
                    Text(stringResource(R.string.region_catalog_cancel_download_button))
                }
            }
        }
    }
}

@Composable
private fun Region.sizeDisplayText(): String =
    if (status == RegionStatus.PARTIALLY_DOWNLOADED) {
        stringResource(R.string.region_catalog_partial_size, estimatedMissingBytes / 1_000_000, approximateSizeBytes / 1_000_000)
    } else {
        stringResource(R.string.region_catalog_size, approximateSizeBytes / 1_000_000)
    }

@Composable
private fun RegionStatus.toDisplayText(): String =
    when (this) {
        RegionStatus.DOWNLOADED -> stringResource(R.string.region_catalog_status_downloaded)
        RegionStatus.DOWNLOADING -> stringResource(R.string.region_catalog_status_downloading)
        RegionStatus.PARTIALLY_DOWNLOADED -> stringResource(R.string.region_catalog_status_partially_downloaded)
        RegionStatus.NOT_DOWNLOADED -> stringResource(R.string.region_catalog_status_not_downloaded)
    }

private fun RegionStatus.toIcon(): ImageVector =
    when (this) {
        RegionStatus.DOWNLOADED -> Icons.Filled.CheckCircle
        RegionStatus.DOWNLOADING -> Icons.Filled.CloudDownload
        RegionStatus.PARTIALLY_DOWNLOADED -> Icons.Filled.CloudSync
        RegionStatus.NOT_DOWNLOADED -> Icons.Filled.CloudOff
    }

@Composable
private fun RegionStatus.toColor(): Color =
    when (this) {
        RegionStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
        RegionStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        RegionStatus.PARTIALLY_DOWNLOADED -> MaterialTheme.colorScheme.tertiary
        RegionStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

@Preview
@Composable
private fun RegionCatalogScreenPreview() {
    RouteForgeTheme {
        RegionCatalogScreen(
            state =
                RegionCatalogState(
                    regions =
                        listOf(
                            Region(
                                id = "berlin",
                                displayName = "Berlin area",
                                minLatitude = 52.3,
                                minLongitude = 13.0,
                                maxLatitude = 52.7,
                                maxLongitude = 13.7,
                                tileIds = listOf("berlin.rd5"),
                                approximateSizeBytes = 45_000_000,
                                status = RegionStatus.DOWNLOADED,
                            ),
                            Region(
                                id = "benelux",
                                displayName = "Benelux area",
                                minLatitude = 50.7,
                                minLongitude = 3.3,
                                maxLatitude = 53.6,
                                maxLongitude = 7.2,
                                tileIds = listOf("benelux.rd5"),
                                approximateSizeBytes = 320_000_000,
                                status = RegionStatus.NOT_DOWNLOADED,
                            ),
                        ),
                    neededRegionIds = setOf("benelux"),
                ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun RegionCatalogScreenEmptyPreview() {
    RouteForgeTheme {
        RegionCatalogScreen(state = RegionCatalogState(), onAction = {})
    }
}
