package com.routeforge.addresssearch.data

import android.util.Log
import com.routeforge.addresssearch.domain.GeocodeClient
import com.routeforge.coredata.toNetworkError
import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.Result
import com.routeforge.coredomain.model.PlaceSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

private const val TAG = "PhotonGeocodeClient"
private const val SEARCH_URL = "https://photon.komoot.io/api/"
private const val MIN_REQUEST_INTERVAL_MILLIS = 1_000L
private const val CACHE_SIZE = 20

/** Manual global search only. Bound repeated requests to the donated public demo service. */
class PhotonGeocodeClient(
    private val httpClient: HttpClient,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : GeocodeClient {
    private val requestMutex = Mutex()
    private val cache = linkedMapOf<String, List<PlaceSearchResult>>()
    private var lastRequestStartedAt: Long? = null

    override suspend fun search(query: String): Result<List<PlaceSearchResult>, DataError.Network> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return Result.Success(emptyList())
        return requestMutex.withLock {
            cache.remove(normalizedQuery)?.let { cached ->
                cache[normalizedQuery] = cached
                return@withLock Result.Success(cached)
            }
            lastRequestStartedAt?.let { previous ->
                val remaining = MIN_REQUEST_INTERVAL_MILLIS - (monotonicMillis() - previous)
                if (remaining > 0) delay(remaining)
            }
            lastRequestStartedAt = monotonicMillis()
            val result = request(normalizedQuery)
            if (result is Result.Success) {
                cache[normalizedQuery] = result.data
                if (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
            }
            result
        }
    }

    private suspend fun request(query: String): Result<List<PlaceSearchResult>, DataError.Network> =
        try {
            val response = httpClient.get(SEARCH_URL) {
                parameter("q", query)
                parameter("limit", GeocodeClient.MAX_SEARCH_RESULTS)
                // No country/bounding-box filter: foreign places must remain searchable.
                // Omitting lang lets Photon return each place's local name.
            }
            if (!response.status.isSuccess()) {
                Log.w(TAG, "Search service returned ${response.status}")
                Result.Error(response.status.toNetworkError())
            } else {
                val body = response.body<PhotonSearchResponseDto>()
                val places = body.features.mapIndexedNotNull { index, feature ->
                    // Photon supplies relevance order, not Nominatim's importance value.
                    feature.toPlaceSearchResult(importance = 1.0 / (index + 1))
                }.take(GeocodeClient.MAX_SEARCH_RESULTS)
                Result.Success(places)
            }
        } catch (error: HttpRequestTimeoutException) {
            // Ktor's request timeout must be handled before general coroutine cancellation.
            Log.w(TAG, "Search timed out", error)
            Result.Error(DataError.Network.REQUEST_TIMEOUT)
        } catch (error: SocketTimeoutException) {
            Log.w(TAG, "Search connection timed out", error)
            Result.Error(DataError.Network.REQUEST_TIMEOUT)
        } catch (error: SerializationException) {
            Log.w(TAG, "Invalid search response", error)
            Result.Error(DataError.Network.SERIALIZATION)
        } catch (error: JsonConvertException) {
            Log.w(TAG, "Could not convert search response", error)
            Result.Error(DataError.Network.SERIALIZATION)
        } catch (error: UnknownHostException) {
            Log.w(TAG, "Could not resolve search service", error)
            Result.Error(DataError.Network.NO_INTERNET)
        } catch (error: IOException) {
            Log.w(TAG, "Search connection failed", error)
            Result.Error(DataError.Network.UNKNOWN)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Unexpected search failure", error)
            Result.Error(DataError.Network.UNKNOWN)
        }
}
