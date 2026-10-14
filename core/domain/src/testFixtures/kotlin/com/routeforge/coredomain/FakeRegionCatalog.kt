package com.routeforge.coredomain

import com.routeforge.coredomain.RegionCatalog
import com.routeforge.coredomain.RegionDeletionResult

import com.routeforge.coredomain.model.Region

class FakeRegionCatalog(
    var regions: List<Region> = emptyList(),
    var sizesOnDiskBytes: Map<String, Long> = emptyMap(),
    var regionIdsThatFailToDelete: Set<String> = emptySet(),
) : RegionCatalog {
    val deletedRegionIds = mutableListOf<String>()

    override fun listRegions(): List<Region> = regions

    override fun regionContaining(
        latitude: Double,
        longitude: Double,
    ): Region? = regions.firstOrNull { it.contains(latitude, longitude) }

    override fun regionById(id: String): Region? = regions.firstOrNull { it.id == id }

    override fun actualSizeOnDiskBytes(region: Region): Long = sizesOnDiskBytes[region.id] ?: 0L

    override fun deleteRegion(region: Region): RegionDeletionResult {
        if (region.id in regionIdsThatFailToDelete) return RegionDeletionResult.NOT_FULLY_DELETED
        deletedRegionIds.add(region.id)
        regions = regions.filterNot { it.id == region.id }
        sizesOnDiskBytes = sizesOnDiskBytes - region.id
        return RegionDeletionResult.FULLY_DELETED
    }
}
