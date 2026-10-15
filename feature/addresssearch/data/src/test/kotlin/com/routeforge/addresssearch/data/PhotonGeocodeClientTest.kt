package com.routeforge.addresssearch.data

import com.routeforge.addresssearch.domain.GeocodeClient
import com.routeforge.coredomain.DataError
import com.routeforge.coredomain.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
private const val EMPTY_RESPONSE = """{"type":"FeatureCollection","features":[]}"""
private const val BEIJING_RESPONSE = """{
    "type":"FeatureCollection",
    "features":[{
        "type":"Feature",
        "properties":{"name":"清河收费站","street":"京藏高速","city":"北京市","country":"中国","osm_id":1147863145},
        "geometry":{"type":"Point","coordinates":[116.3554762,40.0114236]}
    }]
}"""

class PhotonGeocodeClientTest {
    private val httpClients = mutableListOf<HttpClient>()

    private fun client(engine: MockEngine, clock: () -> Long = { 0L }): PhotonGeocodeClient {
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }.also(httpClients::add)
        return PhotonGeocodeClient(http, clock)
    }

    @AfterEach
    fun closeClients() = httpClients.forEach(HttpClient::close)

    @Test
    fun `Chinese queries use Photon without a country or geographic restriction`() = runTest {
        var checked = false
        val engine = MockEngine { request ->
            assertEquals("photon.komoot.io", request.url.host)
            assertEquals("/api/", request.url.encodedPath)
            assertEquals("清河收费站", request.url.parameters["q"])
            assertEquals(GeocodeClient.MAX_SEARCH_RESULTS.toString(), request.url.parameters["limit"])
            assertEquals(setOf("q", "limit"), request.url.parameters.names())
            checked = true
            respond(BEIJING_RESPONSE, headers = jsonHeaders)
        }
        val result = client(engine).search("  清河收费站  ") as Result.Success
        val place = result.data.single()
        assertTrue(checked)
        assertEquals("清河收费站", place.name)
        assertEquals(40.0114236, place.latitude)
        assertEquals(116.3554762, place.longitude)
        assertEquals("清河收费站, 京藏高速, 北京市, 中国", place.formattedAddress)
    }

    @Test
    fun `foreign and western hemisphere coordinates retain their correct signs and order`() = runTest {
        val body = """{"features":[
            {"properties":{"name":"Eiffel Tower","city":"Paris","country":"France"},"geometry":{"type":"Point","coordinates":[2.2945,48.8584]}},
            {"properties":{"name":"New York","country":"United States"},"geometry":{"type":"Point","coordinates":[-74.006,40.7128]}},
            {"properties":{"name":"東京都","country":"日本"},"geometry":{"type":"Point","coordinates":[139.76,35.68]}}
        ]}"""
        val result = client(MockEngine { respond(body, headers = jsonHeaders) }).search("world") as Result.Success
        assertEquals(listOf(48.8584, 40.7128, 35.68), result.data.map { it.latitude })
        assertEquals(listOf(2.2945, -74.006, 139.76), result.data.map { it.longitude })
        assertTrue(result.data[0].importance > result.data[1].importance)
    }

    @Test
    fun `an empty response is a successful empty search`() = runTest {
        val result = client(MockEngine { respond(EMPTY_RESPONSE, headers = jsonHeaders) }).search("unknown")
        assertEquals(Result.Success(emptyList<Any>()), result)
    }

    @Test
    fun `blank text never makes a network request`() = runTest {
        val result = client(MockEngine { error("Must not request") }).search("  ")
        assertEquals(Result.Success(emptyList<Any>()), result)
    }

    @Test
    fun `incomplete or invalid features are skipped while valid places remain usable`() = runTest {
        val body = """{"features":[
            {"properties":{"name":"Missing geometry"}},
            {"properties":{"name":"Out of range"},"geometry":{"type":"Point","coordinates":[181,91]}},
            {"properties":{"name":"Too short"},"geometry":{"type":"Point","coordinates":[1]}},
            {"properties":{"name":"Good"},"geometry":{"type":"Point","coordinates":[0,0]}}
        ]}"""
        val result = client(MockEngine { respond(body, headers = jsonHeaders) }).search("places") as Result.Success
        assertEquals(listOf("Good"), result.data.map { it.name })
    }

    @Test
    fun `invalid point types and nonfinite coordinates cannot become waypoints`() {
        for (geometry in listOf(
            PhotonGeometryDto("LineString", listOf(1.0, 2.0)),
            PhotonGeometryDto("Point", listOf(Double.NaN, 2.0)),
            PhotonGeometryDto("Point", listOf(1.0, Double.POSITIVE_INFINITY)),
            PhotonGeometryDto("Point", listOf(-181.0, 0.0)),
            PhotonGeometryDto("Point", listOf(0.0, -91.0)),
        )) {
            assertNull(PhotonFeatureDto(PhotonPropertiesDto(name = "Bad"), geometry).toPlaceSearchResult(1.0))
        }
    }

    @Test
    fun `unnamed address uses street and avoids duplicate address parts`() {
        val place = PhotonFeatureDto(
            PhotonPropertiesDto(street = "Main Street", city = "Paris", state = "Paris", country = "France"),
            PhotonGeometryDto("Point", listOf(2.0, 48.0)),
        ).toPlaceSearchResult(1.0)!!
        assertEquals("Main Street", place.name)
        assertEquals("Main Street, Paris, France", place.formattedAddress)
    }

    @Test
    fun `malformed JSON produces a response error instead of an empty result`() = runTest {
        val result = client(MockEngine { respond("{broken", headers = jsonHeaders) }).search("Paris")
        assertEquals(Result.Error(DataError.Network.SERIALIZATION), result)
    }

    @Test
    fun `HTTP rate limits and service outages retain distinct errors`() = runTest {
        for ((status, expected) in listOf(
            HttpStatusCode.TooManyRequests to DataError.Network.TOO_MANY_REQUESTS,
            HttpStatusCode.Forbidden to DataError.Network.FORBIDDEN,
            HttpStatusCode.InternalServerError to DataError.Network.SERVER_ERROR,
            HttpStatusCode.ServiceUnavailable to DataError.Network.SERVICE_UNAVAILABLE,
        )) {
            assertEquals(Result.Error(expected), client(MockEngine { respondError(status) }).search("Paris"))
        }
    }

    @Test
    fun `DNS failures are distinct from connection resets`() = runTest {
        assertEquals(Result.Error(DataError.Network.NO_INTERNET), client(MockEngine { throw UnknownHostException() }).search("Paris"))
        assertEquals(Result.Error(DataError.Network.UNKNOWN), client(MockEngine { throw SocketException("Connection reset") }).search("Paris"))
    }

    @Test
    fun `request and socket timeouts are visible timeout errors`() = runTest {
        assertEquals(Result.Error(DataError.Network.REQUEST_TIMEOUT), client(MockEngine { throw SocketTimeoutException() }).search("Paris"))
        assertEquals(
            Result.Error(DataError.Network.REQUEST_TIMEOUT),
            client(MockEngine { throw HttpRequestTimeoutException("https://photon.komoot.io/api/", 20_000L) }).search("Paris"),
        )
    }

    @Test
    fun `cancelling a search is not swallowed as a network error`() = runTest {
        try {
            client(MockEngine { throw CancellationException("cancelled") }).search("Paris")
            fail<Unit>("Expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun `repeated successful query is served from memory without another request`() = runTest {
        var requests = 0
        val client = client(MockEngine {
            requests++
            respond(BEIJING_RESPONSE, headers = jsonHeaders)
        })
        assertEquals(client.search("清河收费站"), client.search(" 清河收费站 "))
        assertEquals(1, requests)
    }

    @Test
    fun `failed searches can be retried and different requests are spaced apart`() = runTest {
        val timestamps = mutableListOf<Long>()
        val client = client(MockEngine {
            timestamps += testScheduler.currentTime
            if (timestamps.size == 1) respondError(HttpStatusCode.ServiceUnavailable)
            else respond(EMPTY_RESPONSE, headers = jsonHeaders)
        }, clock = { testScheduler.currentTime })
        assertTrue(client.search("Paris") is Result.Error)
        assertTrue(client.search("Paris") is Result.Success)
        client.search("Tokyo")
        client.search("Tokyo")
        assertEquals(listOf(0L, 1_000L, 2_000L), timestamps)
    }

    @Test
    fun `memory cache is bounded instead of retaining every submitted query`() = runTest {
        var requests = 0
        val client = client(MockEngine {
            requests++
            respond(EMPTY_RESPONSE, headers = jsonHeaders)
        }, clock = { testScheduler.currentTime })
        repeat(21) { client.search("query $it") }
        client.search("query 0")
        assertEquals(22, requests)
    }
}
