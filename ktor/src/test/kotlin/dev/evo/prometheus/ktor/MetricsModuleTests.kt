package dev.evo.prometheus.ktor

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetricsModuleTests {
    private fun assertContains(content: String, substring: String) {
        assertTrue(
            substring in content,
            "[$substring] substring is not found in [$content]"
        )
    }

    private fun assertNotContains(content: String, substring: String) {
        assertFalse(
            substring in content,
            "[$substring] substring should not be present in [$content]"
        )
    }

    @Test
    fun `metrics module with default metrics`() = withTestApplication({
        metricsModule()
    }) {
        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertContains(content, "# TYPE jvm_threads_current gauge")
            assertContains(content, "# TYPE hiccups histogram")
            assertContains(content, "hiccups_bucket{le=\"+Inf\"} 1.0")
            assertNotContains(content, "http_total_requests")
            assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
        }

        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertContains(content, "http_total_requests_count{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\"} 1.0")
            assertContains(content, "http_total_requests_sum{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\"} ")
            assertContains(content, "http_total_requests_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"+Inf\"} 1.0")
            assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
        }
    }
}
