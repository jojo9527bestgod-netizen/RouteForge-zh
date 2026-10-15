package com.routeforge.coredata

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val USER_AGENT = "RouteForge-zh/1.2.4-zh (+https://github.com/jojo9527bestgod-netizen/RouteForge-zh)"
private const val REQUEST_TIMEOUT_MILLIS = 20_000L

private val JsonConfig = Json { ignoreUnknownKeys = true }

object HttpClientFactory {
    fun create(): HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(JsonConfig) }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
            defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
        }
}
