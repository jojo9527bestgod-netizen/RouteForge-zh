package com.routeforge.coredomain

import com.routeforge.coredomain.RegionUsageTracker

class FakeRegionUsageTracker(
    initialUsage: Map<String, Long> = emptyMap(),
) : RegionUsageTracker {
    private val usage = initialUsage.toMutableMap()

    override fun recordUsed(
        regionIds: Set<String>,
        atMillis: Long,
    ) {
        for (regionId in regionIds) usage[regionId] = atMillis
    }

    override fun lastUsedAt(regionId: String): Long? = usage[regionId]

    override fun clear(regionId: String) {
        usage.remove(regionId)
    }
}
