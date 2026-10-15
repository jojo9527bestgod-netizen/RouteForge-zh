package com.routeforge.addresssearch.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routeforge.coredomain.model.PlaceSearchResult
import com.routeforge.designsystem.components.ConfirmationBottomSheet
import com.routeforge.designsystem.components.NameEntryConfirmationSheet
import com.routeforge.designsystem.theme.RouteForgeTheme
import com.routeforge.designsystem.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val SubmitIndicatorSize = 24.dp
private const val RESULTS_HEIGHT_FRACTION = 0.75f

@Composable
fun AddressSearchRoot(
    sourceContext: SearchSourceContext,
    referenceLatitude: Double?,
    referenceLongitude: Double?,
    onNavigateBack: () -> Unit,
    onOpenSimulate: () -> Unit,
    onOpenPlan: () -> Unit,
    viewModel: AddressSearchViewModel =
        koinViewModel { parametersOf(sourceContext, referenceLatitude, referenceLongitude) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AddressSearchEvent.NavigateBack -> onNavigateBack()
                AddressSearchEvent.NavigateToPlan -> onOpenPlan()
                AddressSearchEvent.NavigateToSimulate -> onOpenSimulate()
            }
        }
    }

    AddressSearchScreen(state = state, onAction = viewModel::onAction, onNavigateBack = onNavigateBack)
}

/** A single bottom sheet (no map preview) — a search field up top, and once a search has been
 *  submitted, a scrollable results list capped at [RESULTS_HEIGHT_FRACTION] of the available
 *  height, matching [com.routeforge.designsystem.waypointedit.WaypointEditSheet]'s established
 *  `BoxWithConstraints`-based fixed-fraction pattern for the same reason: a reactively-measured
 *  height would deadlock here just as it did there. */
@Composable
fun AddressSearchScreen(
    state: AddressSearchState,
    onAction: (AddressSearchAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onNavigateBack, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.ScreenContent)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(AddressSearchAction.OnQueryChange(it)) },
                placeholder = { Text(stringResource(R.string.address_search_query_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction(AddressSearchAction.OnSubmit) }),
                trailingIcon = {
                    IconButton(onClick = { onAction(AddressSearchAction.OnSubmit) }, enabled = !state.isLoading) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(SubmitIndicatorSize))
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.address_search_submit_button))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.address_search_service_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.ControlsRow),
            )

            Text(
                stringResource(R.string.address_search_coordinate_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.ControlsRow),
            )

            if (state.hasSearched) {
                Spacer(modifier = Modifier.height(Spacing.ControlsRow))
                when {
                    state.errorType != null ->
                        Text(
                            stringResource(when (state.errorType) {
                                AddressSearchError.TIMEOUT -> R.string.address_search_error_timeout
                                AddressSearchError.RATE_LIMITED -> R.string.address_search_error_rate_limited
                                AddressSearchError.SERVICE_UNAVAILABLE -> R.string.address_search_error_unavailable
                                AddressSearchError.INVALID_RESPONSE -> R.string.address_search_error_invalid_response
                                AddressSearchError.INVALID_COORDINATES -> R.string.address_search_error_invalid_coordinates
                                else -> R.string.address_search_error_message
                            }),
                            modifier = Modifier.padding(top = Spacing.ScreenContent),
                        )
                    state.results.isEmpty() ->
                        Text(
                            stringResource(R.string.address_search_no_results_message),
                            modifier = Modifier.padding(top = Spacing.ScreenContent),
                        )
                    else ->
                        AddressSearchResultsList(
                            results = state.results,
                            onSelectResult = { index -> onAction(AddressSearchAction.OnSelectResult(index)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                }
            }
        }
    }

    AddressSearchDialogs(state = state, onAction = onAction)
}

/** The screen's modal sheets — the teleport/favorite choice, then (once that choice is "save as
 *  favorite") the name-entry sheet — gated on [AddressSearchState.pendingChoiceResult], matching
 *  [com.routeforge.routing.presentation.routerequest.RouteRequestDialogs]'s precedent of keeping a
 *  screen's modal sheets as one composable, separate from its always-visible content. */
@Composable
private fun AddressSearchDialogs(
    state: AddressSearchState,
    onAction: (AddressSearchAction) -> Unit,
) {
    val choiceResult = state.pendingChoiceResult
    if (choiceResult != null) {
        if (!state.isNamingFavorite) {
            TeleportOrFavoriteChoiceSheet(
                result = choiceResult,
                onChooseTeleport = { onAction(AddressSearchAction.OnChooseTeleport) },
                onChooseAddToRoute = { onAction(AddressSearchAction.OnChooseAddToRoute) },
                onChooseSaveAsFavorite = { onAction(AddressSearchAction.OnChooseSaveAsFavorite) },
                onDismiss = { onAction(AddressSearchAction.OnDismissChoice) },
            )
        } else {
            FavoriteNameSheet(
                nameInput = state.favoriteNameInput,
                onNameChange = { onAction(AddressSearchAction.OnFavoriteNameChange(it)) },
                onConfirm = { onAction(AddressSearchAction.OnConfirmFavoriteName) },
                onDismiss = { onAction(AddressSearchAction.OnDismissFavoriteName) },
            )
        }
    }
}

/** The search results, scrollable, capped at [RESULTS_HEIGHT_FRACTION] of the sheet's available
 *  height — split out from [AddressSearchScreen] so it's independently previewable/testable,
 *  matching [com.routeforge.designsystem.waypointedit.WaypointReorderList]'s precedent of keeping
 *  a sheet's scrollable list as its own composable. */
@Composable
private fun AddressSearchResultsList(
    results: List<PlaceSearchResult>,
    onSelectResult: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val resultsHeight = maxHeight * RESULTS_HEIGHT_FRACTION
        LazyColumn(modifier = Modifier.fillMaxWidth().height(resultsHeight)) {
            itemsIndexed(results) { index, result ->
                ListItem(
                    headlineContent = {
                        Text(if (result.isCoordinate) stringResource(R.string.address_search_coordinate_result_title) else result.name)
                    },
                    supportingContent = {
                        Text(if (result.isCoordinate) {
                            stringResource(R.string.address_search_coordinate_result_address, result.latitude.toString(), result.longitude.toString())
                        } else result.formattedAddress)
                    },
                    modifier = Modifier.clickable { onSelectResult(index) },
                )
            }
        }
    }
}

@Composable
private fun TeleportOrFavoriteChoiceSheet(
    result: PlaceSearchResult,
    onChooseTeleport: () -> Unit,
    onChooseAddToRoute: () -> Unit,
    onChooseSaveAsFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (result.isCoordinate) stringResource(R.string.address_search_coordinate_result_title) else result.name
    ConfirmationBottomSheet(title = title, onDismiss = onDismiss) {
        if (result.isCoordinate) {
            Text(
                stringResource(R.string.address_search_coordinate_result_address, result.latitude.toString(), result.longitude.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onChooseAddToRoute, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.address_search_add_to_route_button))
        }
        TextButton(onClick = onChooseTeleport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.address_search_teleport_button))
        }
        TextButton(onClick = onChooseSaveAsFavorite, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.address_search_save_favorite_button))
        }
    }
}

@Composable
private fun FavoriteNameSheet(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NameEntryConfirmationSheet(
        title = stringResource(R.string.address_search_favorite_name_title),
        nameInput = nameInput,
        onNameChange = onNameChange,
        nameLabel = stringResource(R.string.address_search_favorite_name_label),
        confirmLabel = stringResource(R.string.address_search_favorite_save_button),
        dismissLabel = stringResource(R.string.address_search_cancel_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Preview
@Composable
private fun AddressSearchScreenEmptyPreview() {
    RouteForgeTheme {
        AddressSearchScreen(state = AddressSearchState(), onAction = {}, onNavigateBack = {})
    }
}

@Preview
@Composable
private fun AddressSearchScreenResultsPreview() {
    RouteForgeTheme {
        AddressSearchScreen(
            state =
                AddressSearchState(
                    query = "Coffee",
                    hasSearched = true,
                    results =
                        listOf(
                            PlaceSearchResult(
                                name = "Example Cafe",
                                formattedAddress = "123 Example St, Sample City",
                                latitude = 1.0,
                                longitude = 1.0,
                                importance = 0.5,
                            ),
                            PlaceSearchResult(
                                name = "Another Cafe",
                                formattedAddress = "456 Sample Ave, Sample City",
                                latitude = 2.0,
                                longitude = 2.0,
                                importance = 0.4,
                            ),
                        ),
                ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}

@Preview
@Composable
private fun AddressSearchScreenNoResultsPreview() {
    RouteForgeTheme {
        AddressSearchScreen(
            state = AddressSearchState(query = "Nowhere", hasSearched = true),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
