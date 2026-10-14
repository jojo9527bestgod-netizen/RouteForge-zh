package com.routeforge.routing.presentation.regioncatalog

import com.routeforge.coredomain.holder.DraftWaypointsHolder
import com.routeforge.coredomain.holder.LastComputedRouteHolder
import com.routeforge.coredomain.model.RoutePoint
import com.routeforge.routing.domain.RegionDownloadEvent
import com.routeforge.coredomain.model.Region
import com.routeforge.routing.domain.model.RegionDownloadState
import com.routeforge.coredomain.model.RegionStatus
import com.routeforge.routing.domain.usecase.ComputeRegionUsageUseCase
import com.routeforge.coredomain.usecase.ComputeRequiredRegionsUseCase
import com.routeforge.routing.domain.usecase.DeleteRegionUseCase
import com.routeforge.routing.domain.usecase.ObserveRegionCatalogUseCase
import com.routeforge.routing.presentation.FakeFavoriteRoutesRepository
import com.routeforge.routing.presentation.FakeRegionCatalog
import com.routeforge.routing.presentation.FakeRegionDownloadController
import com.routeforge.routing.presentation.FakeRegionUsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val downloadableRegion =
    Region(
        id = "region-1",
        displayName = "Region 1",
        minLatitude = 0.0,
        minLongitude = 0.0,
        maxLatitude = 1.0,
        maxLongitude = 1.0,
        tileIds = listOf("tile.rd5"),
        approximateSizeBytes = 10L,
        status = RegionStatus.NOT_DOWNLOADED,
    )

private val otherRegion =
    Region(
        id = "region-2",
        displayName = "Region 2",
        minLatitude = 5.0,
        minLongitude = 5.0,
        maxLatitude = 6.0,
        maxLongitude = 6.0,
        tileIds = listOf("other.rd5"),
        approximateSizeBytes = 20L,
        status = RegionStatus.DOWNLOADED,
    )

class RegionCatalogViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val draftWaypointsHolder = DraftWaypointsHolder()
    private val lastComputedRouteHolder = LastComputedRouteHolder()
    private val controller = FakeRegionDownloadController()
    private val regionUsageTracker = FakeRegionUsageTracker()
    private val favoriteRoutesRepository = FakeFavoriteRoutesRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(catalog: FakeRegionCatalog): RegionCatalogViewModel =
        RegionCatalogViewModel(
            observeRegionCatalog = ObserveRegionCatalogUseCase(catalog),
            regionDownloadController = controller,
            computeRequiredRegions = ComputeRequiredRegionsUseCase(catalog),
            draftWaypointsHolder = draftWaypointsHolder,
            deleteRegion = DeleteRegionUseCase(catalog, regionUsageTracker),
            computeRegionUsage =
                ComputeRegionUsageUseCase(
                    computeRequiredRegions = ComputeRequiredRegionsUseCase(catalog),
                    draftWaypointsHolder = draftWaypointsHolder,
                    lastComputedRouteHolder = lastComputedRouteHolder,
                    favoriteRoutesRepository = favoriteRoutesRepository,
                ),
        )

    @Test
    fun `a region that is neither needed nor downloaded is hidden`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)

        assertEquals(emptyList<Region>(), viewModel.state.value.regions)
    }

    @Test
    fun `a region that is already downloaded is shown even when not needed`() {
        val catalog = FakeRegionCatalog(regions = listOf(otherRegion))
        val viewModel = createViewModel(catalog)

        assertEquals(listOf(otherRegion), viewModel.state.value.regions)
    }

    @Test
    fun `regions touched by the current draft are surfaced first and flagged as needed`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion, otherRegion))
        draftWaypointsHolder.set(listOf(RoutePoint(latitude = 5.5, longitude = 5.5)))

        val viewModel = createViewModel(catalog)

        assertEquals(listOf(otherRegion), viewModel.state.value.regions)
        assertEquals(setOf(otherRegion.id), viewModel.state.value.neededRegionIds)
    }

    @Test
    fun `starting a download delegates to the controller with the resolved region`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        draftWaypointsHolder.set(listOf(RoutePoint(latitude = 0.5, longitude = 0.5)))
        val viewModel = createViewModel(catalog)

        viewModel.onAction(RegionCatalogAction.OnDownloadRegion(downloadableRegion.id))

        assertEquals(listOf(downloadableRegion), controller.startCalls)
    }

    @Test
    fun `a second start while one is already active is a no-op, via the controller's own guard`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion, otherRegion))
        draftWaypointsHolder.set(listOf(RoutePoint(latitude = 0.5, longitude = 0.5)))
        val viewModel = createViewModel(catalog)

        viewModel.onAction(RegionCatalogAction.OnDownloadRegion(downloadableRegion.id))
        viewModel.onAction(RegionCatalogAction.OnDownloadRegion(otherRegion.id))

        assertEquals(listOf(downloadableRegion), controller.startCalls)
    }

    @Test
    fun `downloadingRegionId and downloadProgress mirror the controller's state, including the indeterminate case`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)

        controller.emit(RegionDownloadState(downloadableRegion, progress = null))

        assertEquals(downloadableRegion.id, viewModel.state.value.downloadingRegionId)
        assertNull(viewModel.state.value.downloadProgress)

        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))

        assertEquals(0.5f, viewModel.state.value.downloadProgress)
    }

    @Test
    fun `the controller's state going back to null clears downloadingRegionId and refreshes`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)
        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))

        catalog.regions = listOf(downloadableRegion.copy(status = RegionStatus.DOWNLOADED))
        controller.emit(null)

        assertNull(viewModel.state.value.downloadingRegionId)
        assertNull(viewModel.state.value.downloadProgress)
        assertEquals(RegionStatus.DOWNLOADED, viewModel.state.value.regions.first { it.id == downloadableRegion.id }.status)
    }

    @Test
    fun `a failed download event surfaces as a download failed event`() =
        runTest(dispatcher) {
            val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
            val viewModel = createViewModel(catalog)

            val eventDeferred = async { viewModel.events.first() }
            controller.emitEvent(RegionDownloadEvent.Failed(downloadableRegion.id))

            assertEquals(
                RegionCatalogEvent.DownloadFailed("Download failed. Check your connection and try again."),
                eventDeferred.await(),
            )
        }

    @Test
    fun `clicking cancel sets pendingCancelDownload without calling controller cancel`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)
        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))

        viewModel.onAction(RegionCatalogAction.OnCancelDownloadClick)

        assertTrue(viewModel.state.value.pendingCancelDownload)
        assertEquals(0, controller.cancelCallCount)
    }

    @Test
    fun `confirming cancel calls controller cancel and clears the pending flag`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)
        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))
        viewModel.onAction(RegionCatalogAction.OnCancelDownloadClick)

        viewModel.onAction(RegionCatalogAction.OnConfirmCancelDownload)

        assertEquals(1, controller.cancelCallCount)
        assertTrue(!viewModel.state.value.pendingCancelDownload)
    }

    @Test
    fun `dismissing cancel clears the pending flag without cancelling`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)
        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))
        viewModel.onAction(RegionCatalogAction.OnCancelDownloadClick)

        viewModel.onAction(RegionCatalogAction.OnDismissCancelDownload)

        assertEquals(0, controller.cancelCallCount)
        assertTrue(!viewModel.state.value.pendingCancelDownload)
    }

    @Test
    fun `confirming cancel twice in a row has no effect beyond the first call`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)
        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))
        viewModel.onAction(RegionCatalogAction.OnCancelDownloadClick)

        viewModel.onAction(RegionCatalogAction.OnConfirmCancelDownload)
        viewModel.onAction(RegionCatalogAction.OnConfirmCancelDownload)

        assertEquals(1, controller.cancelCallCount)
    }

    @Test
    fun `deleting a region nothing depends on shows no warning and deletes on confirm`() {
        val catalog = FakeRegionCatalog(regions = listOf(otherRegion))
        val viewModel = createViewModel(catalog)

        viewModel.onAction(RegionCatalogAction.OnDeleteRegionClick(otherRegion.id))

        assertEquals(otherRegion, viewModel.state.value.pendingDeleteRegion)
        assertTrue(viewModel.state.value.deleteRegionUsage?.isInUse != true)

        viewModel.onAction(RegionCatalogAction.OnConfirmDeleteRegion)

        assertNull(viewModel.state.value.pendingDeleteRegion)
        assertTrue(catalog.regions.none { it.id == otherRegion.id })
    }

    @Test
    fun `deleting a region needed by the draft, active route, or a saved route warns but still deletes on confirm`() {
        val catalog = FakeRegionCatalog(regions = listOf(otherRegion))
        draftWaypointsHolder.set(listOf(RoutePoint(latitude = otherRegion.minLatitude, longitude = otherRegion.minLongitude)))
        val viewModel = createViewModel(catalog)

        viewModel.onAction(RegionCatalogAction.OnDeleteRegionClick(otherRegion.id))

        assertEquals(true, viewModel.state.value.deleteRegionUsage?.isInUse)
        assertEquals(true, viewModel.state.value.deleteRegionUsage?.neededByDraft)

        viewModel.onAction(RegionCatalogAction.OnConfirmDeleteRegion)

        assertNull(viewModel.state.value.pendingDeleteRegion)
        assertTrue(catalog.regions.none { it.id == otherRegion.id })
    }

    @Test
    fun `dismissing delete clears the pending state without deleting`() {
        val catalog = FakeRegionCatalog(regions = listOf(otherRegion))
        val viewModel = createViewModel(catalog)
        viewModel.onAction(RegionCatalogAction.OnDeleteRegionClick(otherRegion.id))

        viewModel.onAction(RegionCatalogAction.OnDismissDeleteRegion)

        assertNull(viewModel.state.value.pendingDeleteRegion)
        assertNull(viewModel.state.value.deleteRegionUsage)
        assertTrue(catalog.regions.any { it.id == otherRegion.id })
    }

    @Test
    fun `OnDeleteRegionClick is a no-op for the currently-downloading region`() {
        val catalog = FakeRegionCatalog(regions = listOf(downloadableRegion))
        val viewModel = createViewModel(catalog)
        controller.emit(RegionDownloadState(downloadableRegion, progress = 0.5f))

        viewModel.onAction(RegionCatalogAction.OnDeleteRegionClick(downloadableRegion.id))

        assertNull(viewModel.state.value.pendingDeleteRegion)
    }
}
