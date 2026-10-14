package com.routeforge.routing.presentation.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routeforge.coredomain.model.MapStyle
import com.routeforge.designsystem.theme.RouteForgeTheme
import com.routeforge.designsystem.theme.Spacing
import com.routeforge.routing.presentation.R
import org.koin.androidx.compose.koinViewModel

private val SectionSpacing = 16.dp
private val CardShape = RoundedCornerShape(24.dp)
private val CardPadding = 20.dp
private val ChipSpacing = 8.dp

private val QuotaOptions: List<Pair<Long?, Int>> =
    listOf(
        500_000_000L to R.string.settings_quota_500mb,
        1_000_000_000L to R.string.settings_quota_1gb,
        2_000_000_000L to R.string.settings_quota_2gb,
        5_000_000_000L to R.string.settings_quota_5gb,
        null to R.string.settings_quota_unlimited,
    )

private val MapStyleOptions: List<Pair<MapStyle, Int>> =
    listOf(
        MapStyle.OSM_FR to R.string.settings_map_style_osm_fr,
        MapStyle.STANDARD to R.string.settings_map_style_standard,
        MapStyle.TOPOGRAPHIC to R.string.settings_map_style_topographic,
    )

@Composable
fun SettingsRoot(
    onManageDownloadsClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onManageDownloadsClick = onManageDownloadsClick)
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onManageDownloadsClick: () -> Unit = {},
) {
    var showLicenses by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(Spacing.ScreenContent)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            StorageQuotaSection(
                quotaBytes = state.quotaBytes,
                usedBytes = state.usedBytes,
                onQuotaSelected = { onAction(SettingsAction.OnQuotaSelected(it)) },
                onManageDownloadsClick = onManageDownloadsClick,
            )
            MapStyleSection(
                mapStyle = state.mapStyle,
                onMapStyleSelected = { onAction(SettingsAction.OnMapStyleSelected(it)) },
            )
            TextButton(onClick = { showLicenses = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_open_source_button))
            }
        }
    }
    if (showLicenses) {
        val context = LocalContext.current
        val notices = remember(context) {
            context.assets.open("OPEN_SOURCE_NOTICES.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        ModalBottomSheet(
            onDismissRequest = { showLicenses = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(Spacing.ScreenContent)) {
                Text(stringResource(R.string.settings_open_source_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(notices.chunked(4000)) { section ->
                        Text(section, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(R.string.settings_open_source_close))
                }
            }
        }
    }
}

@Composable
private fun StorageQuotaSection(
    quotaBytes: Long?,
    usedBytes: Long,
    onQuotaSelected: (Long?) -> Unit,
    onManageDownloadsClick: () -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Text(
                text = stringResource(R.string.settings_storage_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            UsageSummary(usedBytes = usedBytes, quotaBytes = quotaBytes)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            ) {
                QuotaOptions.forEach { (optionBytes, labelRes) ->
                    FilterChip(
                        selected = optionBytes == quotaBytes,
                        onClick = { onQuotaSelected(optionBytes) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onManageDownloadsClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_manage_downloads_button))
            }
        }
    }
}

@Composable
private fun MapStyleSection(
    mapStyle: MapStyle,
    onMapStyleSelected: (MapStyle) -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Text(
                text = stringResource(R.string.settings_map_style_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            ) {
                MapStyleOptions.forEach { (option, labelRes) ->
                    FilterChip(
                        selected = option == mapStyle,
                        onClick = { onMapStyleSelected(option) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageSummary(
    usedBytes: Long,
    quotaBytes: Long?,
) {
    Column {
        Text(
            text =
                if (quotaBytes == null) {
                    stringResource(R.string.settings_usage_unlimited_label, usedBytes.toDisplayMegabytes())
                } else {
                    stringResource(R.string.settings_usage_label, usedBytes.toDisplayMegabytes(), quotaBytes.toDisplayMegabytes())
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        if (quotaBytes != null) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (quotaBytes == 0L) 1f else (usedBytes.toFloat() / quotaBytes.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

private fun Long.toDisplayMegabytes(): Long = this / 1_000_000L

@Preview
@Composable
private fun SettingsScreenPreview() {
    RouteForgeTheme {
        SettingsScreen(state = SettingsState(quotaBytes = 1_000_000_000L, usedBytes = 650_000_000L), onAction = {})
    }
}
