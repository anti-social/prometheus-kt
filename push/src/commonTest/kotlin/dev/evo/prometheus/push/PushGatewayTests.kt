package dev.evo.prometheus.push

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.runTest

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.hostWithPort

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort

private val Url.fullUrl: String get() = "${protocol.name}://$hostWithPortIfRequired$fullPath"

class PushGatewayTests {
    class JobMetrics : PrometheusMetrics() {
        val elapsedTime by gauge("elapsed_time")
    }

    class JobLabels(host: String? = null, path: String? = null) : LabelSet() {
        var host by label()
        var path by label()

        init {
            this.host = host
            this.path = path
        }
    }

    companion object {
        private suspend fun getMetrics(): PrometheusMetrics {
            return JobMetrics().apply {
                runTest {
                    elapsedTime.set(10.8)
                }
            }
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.configure(
        expectedMethod: HttpMethod, expectedUrl: String
    ) {
        engine {
            addHandler { request ->
                request.assertPush(expectedMethod, expectedUrl)
                respond("")
            }
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.configureFail(
        expectedMethod: HttpMethod, expectedUrl: String
    ) {
        engine {
            addHandler { request ->
                request.assertPush(expectedMethod, expectedUrl)
                respondBadRequest()
            }
        }
    }

    private fun HttpRequestData.assertPush(expectedMethod: HttpMethod, expectedUrl: String) {
        assertEquals(
            expectedUrl, url.fullUrl
        )
        assertEquals(expectedMethod, method)
        if (expectedMethod != HttpMethod.Delete) {
            val body = body as TextContent
            assertEquals("""
                # TYPE elapsed_time gauge
                elapsed_time 10.8

                """.trimIndent(),
                body.text
            )
        }
    }

    @Test
    fun testNotOkStatusCode() = runTest {
        val metrics = getMetrics()
        val client = HttpClient(MockEngine) {
            configureFail(HttpMethod.Put, "http://example.com:9090/metrics/job/test_job")
        }

        val pushGateway = PushGateway("example.com", 9090, client)
        assertFailsWith<PushGatewayException> {
            pushGateway.push(metrics, "test_job")
        }
    }

    @Test
    fun testSimplePush() = runTest {
        val metrics = getMetrics()
        val client = HttpClient(MockEngine) {
            configure(HttpMethod.Put, "http://example.com:9091/metrics/job/test_job")
        }
        val pushGateway = PushGateway("example.com", client)
        pushGateway.push(metrics, "test_job")
    }

    @Test
    fun testSimplePushNoReplace() = runTest {
        val metrics = getMetrics()
        val client = HttpClient(MockEngine) {
            configure(
                HttpMethod.Post,
                "http://example.com:9091/metrics/job/test_job/host/localhost/path@base64/L3RtcA=="
            )
        }
        val pushGateway = PushGateway("example.com", client)
        pushGateway.push(metrics, "test_job", JobLabels("localhost", "/tmp"), replace = false)
    }

    @Test
    fun testUrlEncodedJob() = runTest {
        val metrics = getMetrics()
        val client = HttpClient(MockEngine) {
            configure(HttpMethod.Put, "http://example.com:9091/metrics/job/%3F%25%23")
        }
        val pushGateway = PushGateway("example.com", client)
        pushGateway.push(metrics, "?%#")
    }

    @Test
    fun testBase64EncodedJob() = runTest {
        val metrics = getMetrics()
        val client = HttpClient(MockEngine) {
            configure(
                HttpMethod.Put,
                "http://example.com:9091/metrics/job@base64/cy9kYXkvbmlnaHQv"
            )
        }
        val pushGateway = PushGateway("example.com", client)
        pushGateway.push(metrics, "s/day/night/")
    }

    @Test
    fun testDelete() = runTest {
        val client = HttpClient(MockEngine) {
            configure(HttpMethod.Delete, "http://example.com:9091/metrics/job/test_job")
        }
        val pushGateway = PushGateway("example.com", client)
        pushGateway.delete("test_job")
    }

    @Test
    fun testDeleteWithLabels() = runTest {
        val client = HttpClient(MockEngine) {
            configure(
                HttpMethod.Delete,
                "http://example.com:9091/metrics/job/test_job/path@base64/L3Zhci90bXA="
            )
        }
        val pushGateway = PushGateway("example.com", client)
        pushGateway.delete("test_job", JobLabels(path = "/var/tmp"))
    }
}
