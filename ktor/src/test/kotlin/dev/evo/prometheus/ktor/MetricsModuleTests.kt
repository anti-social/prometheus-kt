package dev.evo.prometheus.ktor

import dev.evo.prometheus.PrometheusMetrics
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.getOrFail
import kotlinx.coroutines.delay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@UseExperimental(io.ktor.util.KtorExperimentalAPI::class)
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
        metricsModule<DefaultMetrics>()
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
            assertContains(content, "http_total_requests_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"1.0\"} ")
            assertContains(content, "http_total_requests_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"2.0\"} ")
            assertContains(content, "http_total_requests_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"10.0\"} ")
            assertContains(content, "http_total_requests_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"100.0\"} ")
            assertContains(content, "http_total_requests_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"+Inf\"} 1.0")
            assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
        }
    }

    @Test
    fun `custom http metrics`() = withTestApplication({
        class CustomMetrics : PrometheusMetrics() {
            val requestDuration by histogram("request_duration", listOf(100.0, 500.0, 1000.0)) {
                HttpRequestLabels()
            }
        }
        val metrics = CustomMetrics()
        metricsModule(object : MetricsConfigurator<CustomMetrics>(metrics) {
            override fun configureFeature(conf: MetricsFeature.Configuration) {
                conf.totalRequests = metrics.requestDuration
            }
        })
    }) {
        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertNotContains(content, "# TYPE jvm_threads_current gauge")
            assertNotContains(content, "http_in_flight_requests")
            assertNotContains(content, "request_duration_count")
        }
        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertNotContains(content, "# TYPE jvm_threads_current gauge")
            assertNotContains(content, "http_in_flight_requests")
            assertContains(content, "# TYPE request_duration histogram")
            assertContains(content, "request_duration_count{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\"} 1.0")
            assertContains(content, "request_duration_sum{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\"} ")
            assertContains(content, "request_duration_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"100.0\"} 1.0")
            assertContains(content, "request_duration_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"500.0\"} 1.0")
            assertContains(content, "request_duration_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"1000.0\"} 1.0")
            assertContains(content, "request_duration_bucket{response_code=\"200\",route=\"/metrics/(method:GET)\",method=\"GET\",le=\"+Inf\"} 1.0")
        }
    }

    @Test
    fun `custom module configuration`() = withTestApplication({
        val configurator = DefaultMetricsConfigurator()
        install(MetricsFeature) {
            configurator.configureFeature(this)
        }

        routing {
            get("/hello") {
                call.respondText("Hello")
            }
            put("/slow/{delay}") {
                // TODO: Find out how to use [TestCoroutineScope.advenceTimeBy] instead of delay
                delay(call.parameters.getOrFail<Long>("delay"))
                call.respondText("It was really slooow!")
            }
            route("/nested") {
                metrics(configurator.metrics)
            }
        }
    }) {
        with(handleRequest(HttpMethod.Get, "/hello")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }
        with(handleRequest(HttpMethod.Put, "/slow")) {
            assertEquals(null, response.status())
        }
        with(handleRequest(HttpMethod.Put, "/slow/110")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }
        with(handleRequest(HttpMethod.Get, "/nested/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)

            val helloLabels = "response_code=\"200\",route=\"/hello/(method:GET)\",method=\"GET\""
            assertContains(content, "http_total_requests_count{$helloLabels} 1.0")
            assertContains(content, "http_total_requests_sum{$helloLabels} ")
            assertContains(content, "http_total_requests_bucket{$helloLabels,le=\"+Inf\"} 1.0")

            val slowLabels = "response_code=\"200\",route=\"/slow/{delay}/(method:PUT)\",method=\"PUT\""
            assertContains(content, "http_total_requests_count{$slowLabels} 1.0")
            assertContains(content, "http_total_requests_sum{$slowLabels} ")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"1.0\"} 0.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"10.0\"} 0.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"100.0\"} 0.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"200.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"1000.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"+Inf\"} 1.0")
        }
    }
}
