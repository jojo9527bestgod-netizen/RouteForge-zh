package com.routeforge.routing.data

import android.util.Log
import btools.mapaccess.PhysicalFile
import com.routeforge.coredata.toNetworkError
import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.EmptyResult
import com.routeforge.coredomain.Result
import com.routeforge.routing.domain.RegionDownloader
import com.routeforge.coredomain.model.Region
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "HttpRegionDownloader"
private const val SEGMENT_BASE_URL = "https://brouter.de/brouter/segments4/"
private const val DOWNLOAD_BUFFER_SIZE_BYTES = 8 * 1024

class HttpRegionDownloader(
    private val httpClient: HttpClient,
    private val regionCatalog: BundledRegionCatalog,
    private val checkIntegrity: (File) -> String? = PhysicalFile::checkFileIntegrity,
) : RegionDownloader {
    override suspend fun download(
        region: Region,
        onProgress: (fraction: Float?) -> Unit,
    ): EmptyResult<DataError.Network> {
        val segmentDirectory = regionCatalog.segmentDirectory
        segmentDirectory.mkdirs()
        val tileCount = region.tileIds.size

        regionCatalog.markDownloading(region.id)
        try {
            region.tileIds.forEachIndexed { index, tileId ->
                val result =
                    downloadTile(segmentDirectory, tileId) { tileFraction ->
                        onProgress(tileFraction?.let { (index + it) / tileCount })
                    }
                if (result is Result.Error) return result
            }

            onProgress(1f)
            return Result.Success(Unit)
        } finally {
            regionCatalog.markDownloadFinished(region.id)
        }
    }

    /** Uses `prepareGet(...).execute { }` (a genuinely streaming request) rather than plain
     *  `get(...)` — Ktor's `SaveBody` plugin, installed by default, unconditionally buffers the
     *  *entire* response body in memory for any non-streaming request before this code ever runs,
     *  regardless of whether the body is later read via `body()` or `bodyAsChannel()`. Only
     *  `HttpStatement.execute` skips that buffering, which is required for [writeChannelToFile]
     *  below to actually avoid materializing a whole (possibly huge) segment file in memory. */
    private suspend fun downloadTile(
        segmentDirectory: File,
        tileId: String,
        onTileProgress: (Float?) -> Unit,
    ): EmptyResult<DataError.Network> {
        val destination = File(segmentDirectory, tileId)
        val partial = File(segmentDirectory, "$tileId.part")
        return try {
            httpClient
                .prepareGet(SEGMENT_BASE_URL + tileId) {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        connectTimeoutMillis = 20_000L
                        socketTimeoutMillis = 30_000L
                    }
                    onDownload { bytesSentTotal, contentLength ->
                        if (contentLength != null && contentLength > 0) {
                            onTileProgress(bytesSentTotal.toFloat() / contentLength)
                        } else {
                            onTileProgress(null)
                        }
                    }
                }.execute { response: HttpResponse ->
                    if (response.status.isSuccess()) {
                        writeChannelToFile(response.bodyAsChannel(), partial)
                        val expectedBytes = response.contentLength()
                        if (expectedBytes != null && partial.length() != expectedBytes) {
                            throw IOException("Incomplete road segment download")
                        }
                        val integrityError = checkIntegrity(partial)
                        if (integrityError != null) throw IOException("Invalid road segment: $integrityError")
                        Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        Result.Success(Unit)
                    } else {
                        Result.Error(response.status.toNetworkError())
                    }
                }
        } catch (e: HttpRequestTimeoutException) {
            // Must be caught before CancellationException below — Ktor's HttpTimeout plugin throws
            // a subclass of it, which would otherwise be silently rethrown as a plain cancellation
            // instead of surfacing as a visible, accurate error.
            Log.w(TAG, "Tile download timed out", e)
            Result.Error(DataError.Network.REQUEST_TIMEOUT)
        } catch (e: UnknownHostException) {
            // The one case that genuinely means "no network/DNS" — distinct from any other
            // IOException (connection reset, SSL handshake, etc.), which isn't actually a
            // lack-of-internet problem.
            Log.w(TAG, "Couldn't resolve the download host", e)
            Result.Error(DataError.Network.NO_INTERNET)
        } catch (e: IOException) {
            Log.w(TAG, "Tile download failed", e)
            Result.Error(DataError.Network.UNKNOWN)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected tile download failure", e)
            Result.Error(DataError.Network.UNKNOWN)
        } finally {
            partial.delete()
        }
    }

    /** Streams the response directly to disk in fixed-size chunks instead of materializing the
     *  whole tile in memory first — segment files can be large enough that reading one fully
     *  into memory risks an OutOfMemoryError. */
    private suspend fun writeChannelToFile(
        channel: ByteReadChannel,
        destination: File,
    ) {
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE_BYTES)
        destination.outputStream().use { output ->
            while (true) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}
