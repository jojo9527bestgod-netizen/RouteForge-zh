package com.routeforge.routing.data

import com.routeforge.coredomain.model.RegionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

private val manifestJson =
    """
    [
      {
        "id": "downloadable-region",
        "displayName": "Downloadable Region",
        "minLatitude": 2.0,
        "minLongitude": 2.0,
        "maxLatitude": 3.0,
        "maxLongitude": 3.0,
        "tileIds": ["downloadable.rd5"],
        "approximateSizeBytes": 20
      },
      {
        "id": "multi-tile-region",
        "displayName": "Multi Tile Region",
        "minLatitude": 4.0,
        "minLongitude": 4.0,
        "maxLatitude": 5.0,
        "maxLongitude": 5.0,
        "tileIds": ["tile-a.rd5", "tile-b.rd5"],
        "approximateSizeBytes": 200
      }
    ]
    """.trimIndent()

private fun fakeAssetOpener(assetPath: String): InputStream =
    when (assetPath) {
        "regions-manifest.json" -> ByteArrayInputStream(manifestJson.toByteArray())
        else -> ByteArrayInputStream(ByteArray(0))
    }

class BundledRegionCatalogTest {
    @Test
    fun `region not yet downloaded reports not downloaded until its tile exists on disk`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)

        val beforeDownload = catalog.listRegions().first { it.id == "downloadable-region" }
        assertEquals(RegionStatus.NOT_DOWNLOADED, beforeDownload.status)

        File(segmentDir, "downloadable.rd5").writeCompleteTileHeader()
        val afterDownload = catalog.listRegions().first { it.id == "downloadable-region" }

        assertEquals(RegionStatus.DOWNLOADED, afterDownload.status)
    }

    @Test
    fun `marking a region as downloading reports downloading status until marked finished`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)

        catalog.markDownloading("downloadable-region")
        assertEquals(
            RegionStatus.DOWNLOADING,
            catalog.listRegions().first { it.id == "downloadable-region" }.status,
        )

        catalog.markDownloadFinished("downloadable-region")
        assertEquals(
            RegionStatus.NOT_DOWNLOADED,
            catalog.listRegions().first { it.id == "downloadable-region" }.status,
        )
    }

    @Test
    fun `a region with only some tiles present reports partially downloaded with the missing tile count`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)
        File(segmentDir, "tile-a.rd5").writeCompleteTileHeader()

        val region = catalog.listRegions().first { it.id == "multi-tile-region" }

        assertEquals(RegionStatus.PARTIALLY_DOWNLOADED, region.status)
        assertEquals(1, region.missingTileCount)
        assertEquals(100L, region.estimatedMissingBytes)
    }

    @Test
    fun `a coordinate with no matching manifest entry resolves to a computed grid tile`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)

        val region = catalog.regionContaining(latitude = -8.05, longitude = -34.9)

        assertEquals("W35_S10", region?.id)
        assertEquals(listOf("W35_S10.rd5"), region?.tileIds)
        assertEquals(RegionStatus.NOT_DOWNLOADED, region?.status)
    }

    @Test
    fun `a computed tile becomes downloaded once its file exists on disk`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)
        File(segmentDir, "W35_S10.rd5").writeCompleteTileHeader()

        val region = catalog.regionContaining(latitude = -8.05, longitude = -34.9)

        assertEquals(RegionStatus.DOWNLOADED, region?.status)
    }

    @Test
    fun `resolving a computed tile id by id works even though it is not in the manifest`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)

        val region = catalog.regionById("W35_S10")

        assertEquals(listOf("W35_S10.rd5"), region?.tileIds)
    }

    @Test
    fun `an id that is neither in the manifest nor a valid grid tile resolves to nothing`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)

        assertEquals(null, catalog.regionById("not-a-real-id"))
    }

    @Test
    fun `a tile downloaded ad hoc without a manifest entry still shows up in the listed regions`(
        @TempDir segmentDir: File,
        @TempDir profileDir: File,
    ) {
        val catalog = BundledRegionCatalog(segmentDir, profileDir, ::fakeAssetOpener)
        File(segmentDir, "W35_S10.rd5").writeCompleteTileHeader()

        val region = catalog.listRegions().firstOrNull { it.id == "W35_S10" }

        assertEquals(RegionStatus.DOWNLOADED, region?.status)
    }
}
