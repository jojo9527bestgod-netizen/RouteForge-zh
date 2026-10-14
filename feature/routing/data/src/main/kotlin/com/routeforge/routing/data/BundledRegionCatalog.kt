package com.routeforge.routing.data

import android.util.Log
import com.routeforge.routing.domain.BrouterTileGrid
import com.routeforge.coredomain.RegionCatalog
import com.routeforge.coredomain.RegionDeletionResult
import com.routeforge.coredomain.model.Region
import com.routeforge.coredomain.model.RegionStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val TAG = "BundledRegionCatalog"
private const val RD5_EXTENSION = "rd5"

/** A rough average segment size, used only until a tile's real size is known (BRouter's segment
 *  server doesn't publish a size index) — actual sizes vary a lot by how densely mapped the area is. */
private const val ESTIMATED_TILE_SIZE_BYTES = 40_000_000L

/**
 * The region "manager": resolves any coordinate or region id to a downloadable BRouter segment,
 * whether it's one of the hand-curated [RegionManifestEntry] entries (kept for friendly display
 * names on well-known areas) or a tile computed on the fly via [BrouterTileGrid] for anywhere else
 * in the world. Also tracks on-disk download state by checking which `.rd5` files actually exist.
 */
class BundledRegionCatalog(
    val segmentDirectory: File,
    private val profileDirectory: File,
    private val openAsset: (String) -> InputStream,
) : RegionCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    private val downloadingRegionIds = mutableSetOf<String>()

    private val manifest: List<RegionManifestEntry> by lazy { loadManifest() }

    val profileFile: File by lazy {
        segmentDirectory.mkdirs()
        profileDirectory.mkdirs()
        copyAssetIfMissing("profile/car-fast.brf", File(profileDirectory, "car-fast.brf"))
        copyAssetIfMissing("profile/lookups.dat", File(profileDirectory, "lookups.dat"))
        File(profileDirectory, "car-fast.brf")
    }

    init {
        profileFile
    }

    /** The curated manifest entries plus any grid tiles already downloaded ad hoc (so previously
     *  fetched tiles remain visible/manageable even though they're not in the manifest). */
    override fun listRegions(): List<Region> = manifestRegions() + adHocDownloadedRegions()

    override fun regionContaining(
        latitude: Double,
        longitude: Double,
    ): Region? =
        listRegions().firstOrNull { it.contains(latitude, longitude) }
            ?: computedRegion(BrouterTileGrid.tileIdFor(latitude, longitude))

    override fun regionById(id: String): Region? = listRegions().firstOrNull { it.id == id } ?: computedRegion(id)

    override fun actualSizeOnDiskBytes(region: Region): Long =
        region.tileIds.sumOf { fileName -> File(segmentDirectory, fileName).let { if (it.exists()) it.length() else 0L } }

    override fun deleteRegion(region: Region): RegionDeletionResult =
        if (region.tileIds.all { fileName -> val file = File(segmentDirectory, fileName); !file.exists() || file.delete() }) {
            RegionDeletionResult.FULLY_DELETED
        } else {
            RegionDeletionResult.NOT_FULLY_DELETED
        }

    internal fun markDownloading(regionId: String) {
        downloadingRegionIds.add(regionId)
    }

    internal fun markDownloadFinished(regionId: String) {
        downloadingRegionIds.remove(regionId)
    }

    private fun manifestRegions(): List<Region> =
        manifest.map { entry ->
            val missingTileCount = missingTileCount(entry.tileIds)
            entry.toRegion(statusFor(entry.id, entry.tileIds.size, missingTileCount), missingTileCount)
        }

    /** Tiles present on disk that aren't part of the manifest — downloaded on demand for a
     *  previous route and not otherwise discoverable without scanning the segment directory. */
    private fun adHocDownloadedRegions(): List<Region> {
        val manifestFileNames = manifest.flatMap { it.tileIds }.toSet()
        val files = segmentDirectory.listFiles { file -> file.extension == RD5_EXTENSION } ?: emptyArray()
        return files.mapNotNull { file ->
            if (file.name in manifestFileNames) return@mapNotNull null
            computedRegion(file.nameWithoutExtension)
        }
    }

    /** Synthesizes a [Region] straight from a BRouter grid tile id (e.g. "W35_S10"), since the id
     *  alone encodes the tile's bounds — no manifest entry is needed to know what it covers. */
    private fun computedRegion(tileId: String): Region? {
        val bounds = BrouterTileGrid.boundsFor(tileId) ?: return null
        val fileName = BrouterTileGrid.fileNameFor(tileId)
        val missingTileCount = missingTileCount(listOf(fileName))
        return Region(
            id = tileId,
            displayName = "Map tile $tileId",
            minLatitude = bounds.minLatitude,
            minLongitude = bounds.minLongitude,
            maxLatitude = bounds.maxLatitude,
            maxLongitude = bounds.maxLongitude,
            tileIds = listOf(fileName),
            approximateSizeBytes = ESTIMATED_TILE_SIZE_BYTES,
            status = statusFor(tileId, tileCount = 1, missingTileCount = missingTileCount),
            missingTileCount = missingTileCount,
        )
    }

    private fun missingTileCount(tileFileNames: List<String>): Int = tileFileNames.count { !hasCompleteSegmentHeader(File(segmentDirectory, it)) }

    private fun statusFor(
        id: String,
        tileCount: Int,
        missingTileCount: Int,
    ): RegionStatus =
        when {
            id in downloadingRegionIds -> RegionStatus.DOWNLOADING
            tileCount == 0 || missingTileCount == tileCount -> RegionStatus.NOT_DOWNLOADED
            missingTileCount == 0 -> RegionStatus.DOWNLOADED
            else -> RegionStatus.PARTIALLY_DOWNLOADED
        }

    private fun copyAssetIfMissing(
        assetPath: String,
        target: File,
    ) {
        if (target.exists()) return
        try {
            openAsset(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't copy bundled asset $assetPath to $target", e)
        }
    }

    private fun loadManifest(): List<RegionManifestEntry> {
        val text = openAsset("regions-manifest.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
